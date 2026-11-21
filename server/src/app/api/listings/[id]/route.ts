import { and, eq, inArray } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { getListingOr404, listingDto, offerDto, sellerCard, userStats, usersById } from "@/lib/dto";
import { conflict, json, parseBody, route, type RouteParams } from "@/lib/http";
import { assertCanView, assertOwner } from "@/lib/listing-access";
import { assertOwnUploads } from "@/lib/uploads";
import { listingUpdateSchema } from "@/lib/validation";

type P = RouteParams<{ id: string }>;

export const GET = route(async (req, { params }: P) => {
  const user = await requireUser(req);
  const listing = await getListingOr404((await params).id);
  await assertCanView(listing, user);
  const [sellers, stats, saved, thread] = await Promise.all([
    usersById([listing.sellerId]),
    userStats([listing.sellerId]),
    db()
      .select({ id: schema.savedListings.listingId })
      .from(schema.savedListings)
      .where(and(eq(schema.savedListings.userId, user.id), eq(schema.savedListings.listingId, listing.id)))
      .limit(1),
    db()
      .select()
      .from(schema.threads)
      .where(and(eq(schema.threads.listingId, listing.id), eq(schema.threads.buyerId, user.id)))
      .limit(1),
  ]);
  const seller = sellers.get(listing.sellerId)!;
  const myOffers = thread[0] ? await db().select().from(schema.offers).where(eq(schema.offers.threadId, thread[0].id)) : [];
  return json({
    listing: listingDto(listing, { seller: sellerCard(seller, stats.get(seller.id)), saved: saved.length > 0 }),
    myThreadId: thread[0]?.id ?? null,
    myOffers: myOffers.map(offerDto),
  });
});

export const PATCH = route(async (req, { params }: P) => {
  const user = await requireUser(req);
  const listing = await getListingOr404((await params).id);
  assertOwner(listing, user);
  const body = await parseBody(req, listingUpdateSchema);
  if (listing.status === "sold") throw conflict("Sold listings can't be edited.");
  if (body.photos) assertOwnUploads(body.photos, user.id, "listings");

  const patch: Partial<typeof schema.listings.$inferInsert> = { ...body, updatedAt: new Date() };
  if (body.status === "active") patch.reservedForId = null; // un-reserving releases the buyer
  const [updated] = await db()
    .update(schema.listings)
    .set(patch)
    .where(and(eq(schema.listings.id, listing.id), inArray(schema.listings.status, ["active", "reserved"])))
    .returning();
  if (!updated) throw conflict("This listing changed in the meantime. Refresh and try again.");
  return json({ listing: listingDto(updated) });
});

export const DELETE = route(async (req, { params }: P) => {
  const user = await requireUser(req);
  const listing = await getListingOr404((await params).id);
  assertOwner(listing, user);
  // Sold listings stay as the record behind ratings and items-sold counts.
  if (listing.status === "sold") throw conflict("Sold items stay on your profile as a record of the sale.", "sold");
  await db()
    .update(schema.listings)
    .set({ status: "removed", updatedAt: new Date() })
    .where(eq(schema.listings.id, listing.id));
  return json({ ok: true });
});
