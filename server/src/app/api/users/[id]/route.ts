import { and, desc, eq, inArray } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { listingDto, publicUser, sellerCard, userStats, usersById } from "@/lib/dto";
import { json, notFound, route, type RouteParams } from "@/lib/http";
import { isBlockedEitherWay } from "@/lib/listing-access";

/** Public profile: seller card, active listings and recent ratings. */
export const GET = route(async (req, { params }: RouteParams<{ id: string }>) => {
  const viewer = await requireUser(req);
  const { id } = await params;
  const [user] = await db().select().from(schema.users).where(eq(schema.users.id, id)).limit(1);
  if (!user) throw notFound("This student's account no longer exists.");
  const blocked = viewer.id !== user.id && (await isBlockedEitherWay(viewer.id, user.id));

  const [stats, listings, ratings] = await Promise.all([
    userStats([user.id]),
    blocked
      ? Promise.resolve([])
      : db()
          .select()
          .from(schema.listings)
          .where(and(eq(schema.listings.sellerId, user.id), inArray(schema.listings.status, ["active", "reserved", "sold"])))
          .orderBy(desc(schema.listings.createdAt))
          .limit(40),
    db().select().from(schema.ratings).where(eq(schema.ratings.rateeId, user.id)).orderBy(desc(schema.ratings.createdAt)).limit(20),
  ]);
  const raters = await usersById(ratings.map((r) => r.raterId));
  return json({
    user: sellerCard(user, stats.get(user.id)),
    blocked,
    listings: listings.filter((l) => l.campusId === viewer.campusId || viewer.id === user.id).map((l) => listingDto(l)),
    ratings: ratings.map((r) => {
      const rater = raters.get(r.raterId);
      return {
        id: r.id,
        stars: r.stars,
        comment: r.comment,
        rateeRole: r.rateeRole,
        createdAt: r.createdAt.toISOString(),
        rater: rater ? publicUser(rater) : null,
      };
    }),
  });
});
