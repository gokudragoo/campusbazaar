import { and, desc, eq, inArray, ne } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { listingDto, publicUser, usersById } from "@/lib/dto";
import { json, route } from "@/lib/http";

/** My listings (all states except deleted), with the buyers who chatted about each one. */
export const GET = route(async (req) => {
  const user = await requireUser(req);
  const listings = await db()
    .select()
    .from(schema.listings)
    .where(and(eq(schema.listings.sellerId, user.id), ne(schema.listings.status, "removed")))
    .orderBy(desc(schema.listings.createdAt));
  const ids = listings.map((l) => l.id);
  const threads = ids.length ? await db().select().from(schema.threads).where(inArray(schema.threads.listingId, ids)) : [];
  const buyers = await usersById(threads.map((t) => t.buyerId));
  return json({
    listings: listings.map((l) => ({
      ...listingDto(l),
      interestedBuyers: threads
        .filter((t) => t.listingId === l.id)
        .map((t) => buyers.get(t.buyerId))
        .filter((b) => b !== undefined)
        .map((b) => publicUser(b)),
    })),
  });
});
