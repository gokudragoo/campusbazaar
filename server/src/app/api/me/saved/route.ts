import { and, desc, eq, ne } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { listingDto, usersById } from "@/lib/dto";
import { json, route } from "@/lib/http";

/** Saved items, newest saved first. Sold items stay visible (marked) so students know what happened. */
export const GET = route(async (req) => {
  const user = await requireUser(req);
  const rows = await db()
    .select({ listing: schema.listings })
    .from(schema.savedListings)
    .innerJoin(schema.listings, eq(schema.listings.id, schema.savedListings.listingId))
    .where(and(eq(schema.savedListings.userId, user.id), ne(schema.listings.status, "removed")))
    .orderBy(desc(schema.savedListings.createdAt));
  const sellers = await usersById(rows.map((r) => r.listing.sellerId));
  return json({
    listings: rows.map(({ listing }) => ({
      ...listingDto(listing, { saved: true }),
      sellerName: sellers.get(listing.sellerId)?.name ?? "Student",
      sellerAvatarUrl: sellers.get(listing.sellerId)?.avatarUrl ?? null,
    })),
  });
});
