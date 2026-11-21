import { eq, sql } from "drizzle-orm";
import { SUBCHANNELS } from "./campus";
import { db, schema } from "./db";
import type { Campus, User } from "./db/schema";
import { createCommunityChannel, createSubchannel, joinCommunityChannel, leaveCommunityChannel, listSubchannels } from "./nexconn";

/**
 * Makes sure the campus's Nexconn community channel and every standard subchannel exist in the
 * current Nexconn app. Safe to repeat: create calls are idempotent on the Platform Chat API.
 *
 * The Platform API reports success when joining a channel that was never created (for example
 * after switching to a new App Key), so membership checks can't detect a missing channel. A
 * channel with none of its subchannels is treated as unprovisioned and created first.
 */
export async function ensureCampusCommunity(campus: Campus, ownerUserId: string): Promise<void> {
  const existing = await listSubchannels(campus.communityChannelId);
  const missing = SUBCHANNELS.filter((s) => !existing.includes(s.id));
  if (missing.length === 0) return;
  if (existing.length === 0) {
    await createCommunityChannel(campus.communityChannelId, `${campus.name}, ${campus.city}`, ownerUserId);
  }
  for (const sub of missing) await createSubchannel(campus.communityChannelId, sub.id);
}

/**
 * Moves a user into a campus: joins the campus's Nexconn community channel (Campus Square),
 * leaves the previous campus's channel, and keeps member counts in step.
 */
export async function moveUserToCampus(user: User, campus: Campus, opts: { alreadyMember?: boolean } = {}): Promise<Campus> {
  if (user.campusId === campus.id) return campus;
  if (!opts.alreadyMember) {
    await ensureCampusCommunity(campus, user.nexconnUserId);
    await joinCommunityChannel(user.nexconnUserId, campus.communityChannelId);
  }

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
