import { and, eq } from "drizzle-orm";
import { requireCampusUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { getThreadForParticipant, touchThread } from "@/lib/deals";
import { meetupDto } from "@/lib/dto";
import { badRequest, conflict, json, parseBody, route } from "@/lib/http";
import { randomId } from "@/lib/ids";
import { rateLimit } from "@/lib/rate-limit";
import { meetupCreateSchema } from "@/lib/validation";

/** Proposes a meetup (campus spot + time) inside a negotiation thread. Replaces earlier proposals. */
export const POST = route(async (req) => {
  const user = await requireCampusUser(req);
  await rateLimit(`meetup:${user.id}`, 40, 3600);
  const body = await parseBody(req, meetupCreateSchema);
  const thread = await getThreadForParticipant(body.threadId, user);
  const [listing] = await db().select().from(schema.listings).where(eq(schema.listings.id, thread.listingId)).limit(1);
  if (!listing || listing.status === "removed") throw conflict("This listing is no longer available.");
  if (listing.status === "sold") throw conflict("This item has already been sold.");

  const when = new Date(body.scheduledAt);
  const now = Date.now();
  if (when.getTime() < now - 5 * 60 * 1000) throw badRequest("Pick a time in the future.");
  if (when.getTime() > now + 60 * 24 * 60 * 60 * 1000) throw badRequest("Pick a time within the next two months.");

  await db()
    .update(schema.meetups)
    .set({ status: "cancelled", respondedAt: new Date() })
    .where(and(eq(schema.meetups.threadId, thread.id), eq(schema.meetups.status, "proposed")));
  const [meetup] = await db()
    .insert(schema.meetups)
    .values({
      id: randomId(14),
      listingId: thread.listingId,
      threadId: thread.id,
      proposedById: user.id,
      buyerId: thread.buyerId,
      sellerId: thread.sellerId,
      spot: body.spot,
      scheduledAt: when,
      lat: body.lat ?? null,
      lng: body.lng ?? null,
      note: body.note,
    })
    .returning();
  await touchThread(thread.id);
  return json({ meetup: meetupDto(meetup!) }, { status: 201 });
});
