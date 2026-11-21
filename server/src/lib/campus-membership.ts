import { eq, sql } from "drizzle-orm";
import { db, schema } from "./db";
import type { Campus, User } from "./db/schema";
import { joinCommunityChannel, leaveCommunityChannel } from "./nexconn";

/**
 * Moves a user into a campus: joins the campus's Nexconn community channel (Campus Square),
 * leaves the previous campus's channel, and keeps member counts in step.
 */
export async function moveUserToCampus(user: User, campus: Campus, opts: { alreadyMember?: boolean } = {}): Promise<Campus> {
  if (user.campusId === campus.id) return campus;
  if (!opts.alreadyMember) await joinCommunityChannel(user.nexconnUserId, campus.communityChannelId);

  const previousId = user.campusId;
  await db().batch([
    db().update(schema.users).set({ campusId: campus.id }).where(eq(schema.users.id, user.id)),
    db()
      .update(schema.campuses)
      .set({ memberCount: sql`${schema.campuses.memberCount} + 1` })
      .where(eq(schema.campuses.id, campus.id)),
    ...(previousId
      ? [
          db()
            .update(schema.campuses)
            .set({ memberCount: sql`greatest(${schema.campuses.memberCount} - 1, 0)` })
            .where(eq(schema.campuses.id, previousId)),
        ]
      : []),
  ] as const);

  if (previousId) {
    const [previous] = await db().select().from(schema.campuses).where(eq(schema.campuses.id, previousId)).limit(1);
    if (previous) {
      await leaveCommunityChannel(user.nexconnUserId, previous.communityChannelId).catch((e) =>
        console.warn("[campus] leave previous community failed", e instanceof Error ? e.message : e),
      );
    }
  }
  return { ...campus, memberCount: campus.memberCount + 1 };
}
