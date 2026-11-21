import { desc, eq, inArray, or } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { publicUser, usersById } from "@/lib/dto";
import { json, route } from "@/lib/http";

/**
 * All of my negotiation threads with peer + listing summary. The app merges this with the
 * Nexconn conversation list (keyed by the peer's Nexconn user id) to show item context.
 */
export const GET = route(async (req) => {
  const user = await requireUser(req);
  const threads = await db()
    .select()
    .from(schema.threads)
    .where(or(eq(schema.threads.buyerId, user.id), eq(schema.threads.sellerId, user.id)))
    .orderBy(desc(schema.threads.lastActivityAt))
    .limit(200);
  const listingIds = [...new Set(threads.map((t) => t.listingId))];
  const [listings, users] = await Promise.all([
    listingIds.length ? db().select().from(schema.listings).where(inArray(schema.listings.id, listingIds)) : Promise.resolve([]),
    usersById(threads.flatMap((t) => [t.buyerId, t.sellerId])),
  ]);
  const byId = new Map(listings.map((l) => [l.id, l]));
  return json({
    threads: threads.map((t) => {
      const peerId = t.buyerId === user.id ? t.sellerId : t.buyerId;
      const l = byId.get(t.listingId);
      const peer = users.get(peerId);
      return {
        id: t.id,
        role: t.buyerId === user.id ? "buyer" : "seller",
        peer: peer ? publicUser(peer) : null,
        listing: l
          ? { id: l.id, title: l.title, price: l.price, photo: l.photos[0] ?? null, status: l.status, reservedForId: l.reservedForId }
          : null,
        lastActivityAt: t.lastActivityAt.toISOString(),
      };
    }),
  });
});
