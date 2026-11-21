import { del } from "@vercel/blob";
import { and, eq, inArray, or, sql } from "drizzle-orm";
import { db, schema } from "./db";
import type { User } from "./db/schema";
import { env } from "./env";
import { expireAccessTokens, leaveCommunityChannel, updateUserInfo } from "./nexconn";
import { isOwnUpload } from "./uploads";

export const DELETED_NAME = "Deleted student";

/**
 * Deletes a student's account by anonymising it. The user row stays (threads, offers, meetups and
 * ratings belong to the other student's history too) but loses every personal field and can never
 * sign in again. Their unsold listings and wanted posts come down, items reserved for them go back
 * on sale, open offers and meetups are closed, and chat access is revoked on Nexconn.
 */
export async function deleteAccount(user: User): Promise<void> {
  const now = new Date();
  const unsold = await db()
    .select({ photos: schema.listings.photos })
    .from(schema.listings)
    .where(and(eq(schema.listings.sellerId, user.id), inArray(schema.listings.status, ["active", "reserved"])));

  await db().batch([
    db()
      .update(schema.listings)
      .set({ status: "removed", reservedForId: null, updatedAt: now })
      .where(and(eq(schema.listings.sellerId, user.id), inArray(schema.listings.status, ["active", "reserved"]))),
    db()
      .update(schema.listings)
      .set({ status: "active", reservedForId: null, updatedAt: now })
      .where(and(eq(schema.listings.reservedForId, user.id), eq(schema.listings.status, "reserved"))),
    db()
      .update(schema.offers)
      .set({ status: "withdrawn", respondedAt: now })
      .where(and(or(eq(schema.offers.buyerId, user.id), eq(schema.offers.sellerId, user.id)), eq(schema.offers.status, "pending"))),
    db()
      .update(schema.meetups)
      .set({ status: "cancelled", respondedAt: now })
      .where(and(or(eq(schema.meetups.buyerId, user.id), eq(schema.meetups.sellerId, user.id)), eq(schema.meetups.status, "proposed"))),
    db().delete(schema.wantedResponses).where(eq(schema.wantedResponses.responderId, user.id)),
    db().delete(schema.wantedPosts).where(eq(schema.wantedPosts.userId, user.id)),
    db().delete(schema.savedListings).where(eq(schema.savedListings.userId, user.id)),
    db().delete(schema.hiddenListings).where(eq(schema.hiddenListings.userId, user.id)),
    db().delete(schema.blocks).where(or(eq(schema.blocks.blockerId, user.id), eq(schema.blocks.blockedId, user.id))),
    db()
      .update(schema.users)
      .set({
        name: DELETED_NAME,
        email: `deleted-${user.id}@deleted.campusbazaar.invalid`,
        passwordHash: "!",
        avatarUrl: null,
        hostel: null,
        department: null,
        campusId: null,
        deletedAt: now,
      })
      .where(eq(schema.users.id, user.id)),
    ...(user.campusId
      ? [
          db()
            .update(schema.campuses)
            .set({ memberCount: sql`greatest(${schema.campuses.memberCount} - 1, 0)` })
            .where(eq(schema.campuses.id, user.campusId)),
        ]
      : []),
  ] as const);

  // Everything below is best effort: the account is already gone from CampusBazaar's side.
  const warn = (what: string) => (e: unknown) => console.warn(`[account] ${what} failed`, e instanceof Error ? e.message : e);
  const campus = user.campusId
    ? (await db().select().from(schema.campuses).where(eq(schema.campuses.id, user.campusId)).limit(1))[0]
    : undefined;
  const photos = [...unsold.flatMap((l) => l.photos), ...(user.avatarUrl ? [user.avatarUrl] : [])].filter(
    (url) => isOwnUpload(url, user.id, "listings") || isOwnUpload(url, user.id, "avatars"),
  );
  await Promise.all([
    expireAccessTokens([user.nexconnUserId]).catch(warn("expire Nexconn tokens")),
    updateUserInfo(user.nexconnUserId, DELETED_NAME).catch(warn("anonymise Nexconn profile")),
    campus ? leaveCommunityChannel(user.nexconnUserId, campus.communityChannelId).catch(warn("leave Campus Square")) : null,
    photos.length > 0 ? del(photos, { token: env.blobToken() }).catch(warn("delete photos")) : null,
  ]);
}
