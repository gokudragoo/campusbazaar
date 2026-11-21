import { and, count, eq, inArray, isNotNull, sql } from "drizzle-orm";
import { db, schema } from "./db";
import type { Campus, Listing, Meetup, Offer, User, WantedPost } from "./db/schema";

/** Shapes returned to the app. Never include password hashes or emails of other users. */

export interface UserStats {
  ratingAvg: number | null;
  ratingCount: number;
  responseRate: number | null;
  activeListings: number;
  soldCount: number;
}

export function publicUser(u: User) {
  return {
    id: u.id,
    nexconnUserId: u.nexconnUserId,
    name: u.name,
    avatarUrl: u.avatarUrl,
    hostel: u.hostel,
    department: u.department,
    campusId: u.campusId,
    joinedAt: u.createdAt.toISOString(),
  };
}

export type PublicUser = ReturnType<typeof publicUser>;

export function selfUser(u: User) {
  return { ...publicUser(u), email: u.email };
}

export function campusDto(c: Campus) {
  return {
    id: c.id,
    name: c.name,
    city: c.city,
    memberCount: c.memberCount,
    communityChannelId: c.communityChannelId,
  };
}

export async function userStats(userIds: string[]): Promise<Map<string, UserStats>> {
  const out = new Map<string, UserStats>();
  if (userIds.length === 0) return out;
  const unique = [...new Set(userIds)];
  for (const id of unique) out.set(id, { ratingAvg: null, ratingCount: 0, responseRate: null, activeListings: 0, soldCount: 0 });

  const [ratingRows, threadRows, repliedRows, listingRows] = await Promise.all([
    db()
      .select({ id: schema.ratings.rateeId, avg: sql<string>`avg(${schema.ratings.stars})`, n: count() })
      .from(schema.ratings)
      .where(inArray(schema.ratings.rateeId, unique))
      .groupBy(schema.ratings.rateeId),
    db()
      .select({ id: schema.threads.sellerId, n: count() })
      .from(schema.threads)
      .where(inArray(schema.threads.sellerId, unique))
      .groupBy(schema.threads.sellerId),
    db()
      .select({ id: schema.threads.sellerId, n: count() })
      .from(schema.threads)
      .where(and(inArray(schema.threads.sellerId, unique), isNotNull(schema.threads.sellerRepliedAt)))
      .groupBy(schema.threads.sellerId),
    db()
      .select({ id: schema.listings.sellerId, status: schema.listings.status, n: count() })
      .from(schema.listings)
      .where(inArray(schema.listings.sellerId, unique))
      .groupBy(schema.listings.sellerId, schema.listings.status),
  ]);

  for (const r of ratingRows) {
    const s = out.get(r.id)!;
    s.ratingAvg = Math.round(Number(r.avg) * 10) / 10;
    s.ratingCount = Number(r.n);
  }
  const replied = new Map(repliedRows.map((r) => [r.id, Number(r.n)]));
  for (const r of threadRows) {
    const total = Number(r.n);
    // Only show a response rate once a seller has had a few enquiries, so one ignored chat doesn't brand them.
    if (total >= 3) out.get(r.id)!.responseRate = Math.round(((replied.get(r.id) ?? 0) / total) * 100);
  }
  for (const r of listingRows) {
    const s = out.get(r.id)!;
    if (r.status === "active" || r.status === "reserved") s.activeListings += Number(r.n);
    if (r.status === "sold") s.soldCount += Number(r.n);
  }
  return out;
}

export function sellerCard(u: User, stats: UserStats | undefined) {
  return {
    ...publicUser(u),
    ratingAvg: stats?.ratingAvg ?? null,
    ratingCount: stats?.ratingCount ?? 0,
    responseRate: stats?.responseRate ?? null,
    activeListings: stats?.activeListings ?? 0,
    soldCount: stats?.soldCount ?? 0,
  };
}

export function listingDto(l: Listing, extra: { seller?: ReturnType<typeof sellerCard>; saved?: boolean } = {}) {
  return {
    id: l.id,
    campusId: l.campusId,
    sellerId: l.sellerId,
    title: l.title,
    description: l.description,
    category: l.category,
    condition: l.condition,
    price: l.price,
    negotiable: l.negotiable,
    meetupSpot: l.meetupSpot,
    photos: l.photos,
    status: l.status,
    reservedForId: l.reservedForId,
    soldToId: l.soldToId,
    aiAssisted: l.aiAssisted,
    aiPriceMin: l.aiPriceMin,
    aiPriceMax: l.aiPriceMax,
    createdAt: l.createdAt.toISOString(),
    updatedAt: l.updatedAt.toISOString(),
    soldAt: l.soldAt?.toISOString() ?? null,
    ...(extra.seller ? { seller: extra.seller } : {}),
    ...(extra.saved !== undefined ? { saved: extra.saved } : {}),
  };
}

export function offerDto(o: Offer) {
  return {
    id: o.id,
    listingId: o.listingId,
    threadId: o.threadId,
    buyerId: o.buyerId,
    sellerId: o.sellerId,
    fromUserId: o.fromUserId,
    amount: o.amount,
    status: o.status,
    parentOfferId: o.parentOfferId,
    messageId: o.messageId,
    createdAt: o.createdAt.toISOString(),
    respondedAt: o.respondedAt?.toISOString() ?? null,
  };
}

export function meetupDto(m: Meetup) {
  return {
    id: m.id,
    listingId: m.listingId,
    threadId: m.threadId,
    proposedById: m.proposedById,
    buyerId: m.buyerId,
    sellerId: m.sellerId,
    spot: m.spot,
    scheduledAt: m.scheduledAt.toISOString(),
    lat: m.lat,
    lng: m.lng,
    note: m.note,
    status: m.status,
    messageId: m.messageId,
    createdAt: m.createdAt.toISOString(),
  };
}

export function wantedDto(w: WantedPost, author?: User) {
  return {
    id: w.id,
    campusId: w.campusId,
    userId: w.userId,
    title: w.title,
    description: w.description,
    category: w.category,
    budget: w.budget,
    status: w.status,
    responseCount: w.responseCount,
    createdAt: w.createdAt.toISOString(),
    ...(author ? { author: publicUser(author) } : {}),
  };
}

/** Ids of users the viewer has blocked or who have blocked the viewer (hidden both ways). */
export async function blockedUserIds(viewerId: string): Promise<string[]> {
  const rows = await db()
    .select({ a: schema.blocks.blockerId, b: schema.blocks.blockedId })
    .from(schema.blocks)
    .where(sql`${schema.blocks.blockerId} = ${viewerId} or ${schema.blocks.blockedId} = ${viewerId}`);
  return rows.map((r) => (r.a === viewerId ? r.b : r.a));
}

export async function usersById(ids: string[]): Promise<Map<string, User>> {
  const unique = [...new Set(ids.filter(Boolean))];
  if (unique.length === 0) return new Map();
  const rows = await db().select().from(schema.users).where(inArray(schema.users.id, unique));
  return new Map(rows.map((u) => [u.id, u]));
}

export async function getListingOr404(id: string): Promise<Listing> {
  const [row] = await db().select().from(schema.listings).where(eq(schema.listings.id, id)).limit(1);
  if (!row || row.status === "removed") {
    const { notFound } = await import("./http");
    throw notFound("This listing is no longer available.");
  }
  return row;
}
