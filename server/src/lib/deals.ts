import { and, eq, ne, or, sql } from "drizzle-orm";
import { db, schema } from "./db";
import type { Listing, Offer, Thread, User } from "./db/schema";
import { badRequest, conflict, forbidden, notFound } from "./http";
import { randomId } from "./ids";
import { isBlockedEitherWay } from "./listing-access";
import { sendSystemNotice, type NoticeContent } from "./nexconn";

/** Offer, meetup and thread state machine. The server is the source of truth; chat cards mirror it. */

export const rupees = (n: number) => `₹${n.toLocaleString("en-IN")}`;

export async function notify(users: User[], notice: NoticeContent): Promise<void> {
  try {
    await sendSystemNotice(
      users.map((u) => u.nexconnUserId),
      notice,
    );
  } catch (err) {
    console.warn("[notice] failed", err instanceof Error ? err.message : err);
  }
}

/** Finds or creates the negotiation thread between a buyer and the listing's seller. */
export async function ensureThread(listing: Listing, buyer: User): Promise<{ thread: Thread; created: boolean }> {
  if (listing.sellerId === buyer.id) throw badRequest("This is your own listing.");
  if (listing.campusId !== buyer.campusId) throw forbidden("You can only chat about items on your campus.");
  if (listing.status === "removed") throw notFound("This listing is no longer available.");
  if (await isBlockedEitherWay(buyer.id, listing.sellerId)) throw forbidden("You can't message this student.");

  const [inserted] = await db()
    .insert(schema.threads)
    .values({ id: randomId(14), listingId: listing.id, buyerId: buyer.id, sellerId: listing.sellerId })
    .onConflictDoNothing({ target: [schema.threads.listingId, schema.threads.buyerId] })
    .returning();
  if (inserted) return { thread: inserted, created: true };
  const [existing] = await db()
    .select()
    .from(schema.threads)
    .where(and(eq(schema.threads.listingId, listing.id), eq(schema.threads.buyerId, buyer.id)))
    .limit(1);
  return { thread: existing!, created: false };
}

export async function getThreadForParticipant(threadId: string, user: User): Promise<Thread> {
  const [thread] = await db().select().from(schema.threads).where(eq(schema.threads.id, threadId)).limit(1);
  if (!thread) throw notFound("This conversation no longer exists.");
  if (thread.buyerId !== user.id && thread.sellerId !== user.id) throw forbidden();
  return thread;
}

export async function touchThread(threadId: string): Promise<void> {
  await db().update(schema.threads).set({ lastActivityAt: new Date() }).where(eq(schema.threads.id, threadId));
}

/** Offers that a newly created offer supersedes (pending offers in the same thread). */
async function supersedePending(threadId: string, actorId: string, exceptId?: string): Promise<Offer[]> {
  const pending = await db()
    .select()
    .from(schema.offers)
    .where(and(eq(schema.offers.threadId, threadId), eq(schema.offers.status, "pending"), exceptId ? ne(schema.offers.id, exceptId) : undefined));
  const out: Offer[] = [];
  for (const o of pending) {
    const status = o.fromUserId === actorId ? "withdrawn" : "countered";
    const [u] = await db()
      .update(schema.offers)
      .set({ status, respondedAt: new Date() })
      .where(and(eq(schema.offers.id, o.id), eq(schema.offers.status, "pending")))
      .returning();
    if (u) out.push(u);
  }
  return out;
}

/** Deal actions between two students stop working once either has blocked the other. */
export async function assertNotBlocked(buyerId: string, sellerId: string): Promise<void> {
  if (await isBlockedEitherWay(buyerId, sellerId)) throw forbidden("You can't make deals with this student.");
}

function assertNegotiable(listing: Listing, amount: number): void {
  if (listing.status === "sold") throw conflict("This item has already been sold.");
  if (listing.status === "removed") throw notFound("This listing is no longer available.");
  if (!listing.negotiable && amount !== listing.price) {
    throw badRequest(`The seller set a fixed price of ${rupees(listing.price)}.`, "fixed_price");
  }
}

/** Buyer makes an offer (or the seller makes one inside an existing thread). */
export async function createOffer(listing: Listing, actor: User, amount: number, threadIdHint?: string) {
  assertNegotiable(listing, amount);
  let thread: Thread;
  if (actor.id === listing.sellerId) {
    if (!threadIdHint) throw badRequest("Pick the buyer conversation to send an offer in.");
    thread = await getThreadForParticipant(threadIdHint, actor);
    if (thread.listingId !== listing.id) throw badRequest("That conversation is about a different item.");
    await assertNotBlocked(thread.buyerId, thread.sellerId);
  } else {
    if (listing.status === "reserved" && listing.reservedForId !== actor.id) {
      throw conflict("This item is reserved for another buyer right now.", "reserved");
    }
    thread = (await ensureThread(listing, actor)).thread;
  }
  const superseded = await supersedePending(thread.id, actor.id);
  const [offer] = await db()
    .insert(schema.offers)
    .values({
      id: randomId(14),
      listingId: listing.id,
      threadId: thread.id,
      buyerId: thread.buyerId,
      sellerId: thread.sellerId,
      fromUserId: actor.id,
      amount,
    })
    .returning();
  await touchThread(thread.id);
  return { offer: offer!, superseded, thread };
}

export async function getOfferForParticipant(offerId: string, user: User): Promise<Offer> {
  const [offer] = await db().select().from(schema.offers).where(eq(schema.offers.id, offerId)).limit(1);
  if (!offer) throw notFound("This offer no longer exists.");
  if (offer.buyerId !== user.id && offer.sellerId !== user.id) throw forbidden();
  return offer;
}

function assertRecipient(offer: Offer, user: User): void {
  if (offer.fromUserId === user.id) throw forbidden("You can't respond to your own offer.");
  if (offer.status !== "pending") throw conflict(`This offer was already ${offer.status}.`, "offer_closed");
}

/**
 * Accepting reserves the listing for the buyer at the agreed amount. Both writes happen in one
 * statement: the listing is reserved only while the offer is still pending and the item is free
 * (or already reserved for this buyer), and the offer is accepted only if that reservation
 * happened. Concurrent accepts for different buyers can't both win.
 */
export async function acceptOffer(offer: Offer, actor: User) {
  assertRecipient(offer, actor);
  await assertNotBlocked(offer.buyerId, offer.sellerId);
  const result = await db().execute<{ accepted: number }>(sql`
    with reserved as (
      update listings set status = 'reserved', reserved_for_id = ${offer.buyerId}, updated_at = now()
      where id = ${offer.listingId}
        and (status = 'active' or (status = 'reserved' and (reserved_for_id is null or reserved_for_id = ${offer.buyerId})))
        and exists (select 1 from offers where id = ${offer.id} and status = 'pending')
      returning id
    ), accepted as (
      update offers set status = 'accepted', responded_at = now()
      where id = ${offer.id} and status = 'pending' and exists (select 1 from reserved)
      returning id
    )
    select count(*)::int as accepted from accepted
  `);

  const [[updatedOffer], [listing]] = await Promise.all([
    db().select().from(schema.offers).where(eq(schema.offers.id, offer.id)).limit(1),
    db().select().from(schema.listings).where(eq(schema.listings.id, offer.listingId)).limit(1),
  ]);
  if (Number(result.rows[0]?.accepted ?? 0) !== 1 || !updatedOffer || !listing) {
    if (!listing || listing.status === "removed") throw notFound("This listing is no longer available.");
    if (listing.status === "sold") throw conflict("This item has already been sold.");
    if (updatedOffer && updatedOffer.status !== "pending") throw conflict("This offer was already answered.", "offer_closed");
    throw conflict("This item is already reserved for another buyer.", "reserved");
  }
  await touchThread(offer.threadId);
  return { offer: updatedOffer, listing };
}

export async function declineOffer(offer: Offer, actor: User) {
  assertRecipient(offer, actor);
  const [updated] = await db()
    .update(schema.offers)
    .set({ status: "declined", respondedAt: new Date() })
    .where(and(eq(schema.offers.id, offer.id), eq(schema.offers.status, "pending")))
    .returning();
  if (!updated) throw conflict("This offer was already answered.", "offer_closed");
  await touchThread(offer.threadId);
  return updated;
}

export async function counterOffer(offer: Offer, actor: User, amount: number) {
  assertRecipient(offer, actor);
  await assertNotBlocked(offer.buyerId, offer.sellerId);
  const [listing] = await db().select().from(schema.listings).where(eq(schema.listings.id, offer.listingId)).limit(1);
  if (!listing) throw notFound("This listing is no longer available.");
  assertNegotiable(listing, amount);
  const [countered] = await db()
    .update(schema.offers)
    .set({ status: "countered", respondedAt: new Date() })
    .where(and(eq(schema.offers.id, offer.id), eq(schema.offers.status, "pending")))
    .returning();
  if (!countered) throw conflict("This offer was already answered.", "offer_closed");
  const [counter] = await db()
    .insert(schema.offers)
    .values({
      id: randomId(14),
      listingId: offer.listingId,
      threadId: offer.threadId,
      buyerId: offer.buyerId,
      sellerId: offer.sellerId,
      fromUserId: actor.id,
      amount,
      parentOfferId: offer.id,
    })
    .returning();
  await touchThread(offer.threadId);
  return { countered, offer: counter! };
}

/** Threads (any role) between two users, newest activity first. */
export async function threadsBetween(a: string, b: string): Promise<Thread[]> {
  return db()
    .select()
    .from(schema.threads)
    .where(
      or(
        and(eq(schema.threads.buyerId, a), eq(schema.threads.sellerId, b)),
        and(eq(schema.threads.buyerId, b), eq(schema.threads.sellerId, a)),
      ),
    );
}
