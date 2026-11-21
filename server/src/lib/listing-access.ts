import { and, eq, or } from "drizzle-orm";
import { db, schema } from "./db";
import type { Listing, User } from "./db/schema";
import { forbidden, notFound } from "./http";

/** True when either user has blocked the other. */
export async function isBlockedEitherWay(a: string, b: string): Promise<boolean> {
  const [row] = await db()
    .select({ a: schema.blocks.blockerId })
    .from(schema.blocks)
    .where(
      or(
        and(eq(schema.blocks.blockerId, a), eq(schema.blocks.blockedId, b)),
        and(eq(schema.blocks.blockerId, b), eq(schema.blocks.blockedId, a)),
      ),
    )
    .limit(1);
  return Boolean(row);
}

/** A listing is visible to its seller and to students on the same campus who aren't blocked either way. */
export async function assertCanView(listing: Listing, viewer: User): Promise<void> {
  if (listing.sellerId === viewer.id) return;
  if (listing.status === "removed" || listing.campusId !== viewer.campusId) {
    throw notFound("This listing is no longer available.");
  }
  if (await isBlockedEitherWay(viewer.id, listing.sellerId)) throw notFound("This listing is no longer available.");
}

export function assertOwner(listing: Listing, user: User): void {
  if (listing.sellerId !== user.id) throw forbidden("Only the seller can change this listing.");
}
