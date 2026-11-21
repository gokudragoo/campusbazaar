import { and, desc, eq, inArray } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { threadsBetween } from "@/lib/deals";
import { listingDto, meetupDto, offerDto, sellerCard, userStats } from "@/lib/dto";
import { json, notFound, route, type RouteParams } from "@/lib/http";

/**
 * Deal state between me and a chat peer: every listing we negotiated, with offers, meetups and
 * whether each side has rated. The chat screen renders cards and actions from this.
 */
export const GET = route(async (req, { params }: RouteParams<{ nexconnUserId: string }>) => {
  const user = await requireUser(req);
  const { nexconnUserId } = await params;
  const [peer] = await db().select().from(schema.users).where(eq(schema.users.nexconnUserId, nexconnUserId)).limit(1);
  if (!peer) throw notFound("This student's account no longer exists.");

  const threads = (await threadsBetween(user.id, peer.id)).sort((a, b) => b.lastActivityAt.getTime() - a.lastActivityAt.getTime());
  const threadIds = threads.map((t) => t.id);
  const listingIds = threads.map((t) => t.listingId);
  const [stats, listings, offers, meetups, ratings, blockedByMe] = await Promise.all([
    userStats([peer.id]),
    listingIds.length ? db().select().from(schema.listings).where(inArray(schema.listings.id, listingIds)) : Promise.resolve([]),
    threadIds.length
      ? db().select().from(schema.offers).where(inArray(schema.offers.threadId, threadIds)).orderBy(desc(schema.offers.createdAt))
      : Promise.resolve([]),
    threadIds.length
      ? db().select().from(schema.meetups).where(inArray(schema.meetups.threadId, threadIds)).orderBy(desc(schema.meetups.createdAt))
      : Promise.resolve([]),
    listingIds.length ? db().select().from(schema.ratings).where(inArray(schema.ratings.listingId, listingIds)) : Promise.resolve([]),
    db()
      .select({ id: schema.blocks.blockedId })
      .from(schema.blocks)
      .where(and(eq(schema.blocks.blockerId, user.id), eq(schema.blocks.blockedId, peer.id)))
      .limit(1),
  ]);
  const listingById = new Map(listings.map((l) => [l.id, l]));

  return json({
    peer: sellerCard(peer, stats.get(peer.id)),
    blockedByMe: blockedByMe.length > 0,
    threads: threads.map((t) => {
      const listing = listingById.get(t.listingId);
      return {
        id: t.id,
        role: t.buyerId === user.id ? "buyer" : "seller",
        listing: listing ? listingDto(listing) : null,
        offers: offers.filter((o) => o.threadId === t.id).map(offerDto),
        meetups: meetups.filter((m) => m.threadId === t.id).map(meetupDto),
        iRated: ratings.some((r) => r.listingId === t.listingId && r.raterId === user.id),
        theyRated: ratings.some((r) => r.listingId === t.listingId && r.raterId === peer.id),
        lastActivityAt: t.lastActivityAt.toISOString(),
      };
    }),
  });
});
