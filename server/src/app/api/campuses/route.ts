import { desc, eq, ilike, or } from "drizzle-orm";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { campusKey, SUBCHANNELS, tidyCampusName } from "@/lib/campus";
import { db, schema } from "@/lib/db";
import { campusDto } from "@/lib/dto";
import { json, parseBody, parseQuery, route } from "@/lib/http";
import { campusChannelId, randomId } from "@/lib/ids";
import { createCommunityChannel, createSubchannel } from "@/lib/nexconn";
import { rateLimit } from "@/lib/rate-limit";
import { campusCreateSchema } from "@/lib/validation";
import { moveUserToCampus } from "@/lib/campus-membership";

const querySchema = z.object({ q: z.string().trim().max(80).optional() });

export const GET = route(async (req) => {
  await requireUser(req);
  const { q } = parseQuery(req, querySchema);
  const term = q?.replace(/[%_\\]/g, (c) => `\\${c}`);
  const rows = await db()
    .select()
    .from(schema.campuses)
    .where(term ? or(ilike(schema.campuses.name, `%${term}%`), ilike(schema.campuses.city, `%${term}%`)) : undefined)
    .orderBy(desc(schema.campuses.memberCount), schema.campuses.name)
    .limit(30);
  return json({ campuses: rows.map(campusDto) });
});

/**
 * Adds a campus (deduped by normalised name + city), provisions its Nexconn community channel
 * with the standard subchannels, and moves the creator into it.
 */
export const POST = route(async (req) => {
  const user = await requireUser(req);
  await rateLimit(`campus-create:${user.id}`, 5, 86400, "You've added several campuses today. Pick one from the list instead.");
  const body = await parseBody(req, campusCreateSchema);
  const key = campusKey(body.name, body.city);

  const [existing] = await db().select().from(schema.campuses).where(eq(schema.campuses.normKey, key)).limit(1);
  if (existing) {
    const campus = await moveUserToCampus(user, existing);
    return json({ campus: campusDto(campus), created: false });
  }

  const name = tidyCampusName(body.name);
  const city = tidyCampusName(body.city);
  const channelId = campusChannelId(key);
  await createCommunityChannel(channelId, `${name}, ${city}`, user.nexconnUserId);
  for (const sub of SUBCHANNELS) await createSubchannel(channelId, sub.id);

  const [inserted] = await db()
    .insert(schema.campuses)
    .values({ id: randomId(12), name, city, normKey: key, communityChannelId: channelId, createdById: user.id, memberCount: 0 })
    .onConflictDoNothing({ target: schema.campuses.normKey })
    .returning();
  // A concurrent request may have created the same campus first; fall back to that row.
  const campus = inserted ?? (await db().select().from(schema.campuses).where(eq(schema.campuses.normKey, key)).limit(1))[0]!;
  // Only the request that inserted the row created the channel with this student as its first member.
  const joined = await moveUserToCampus(user, campus, { alreadyMember: Boolean(inserted) });
  return json({ campus: campusDto(joined), created: Boolean(inserted) }, { status: inserted ? 201 : 200 });
});

export const dynamic = "force-dynamic";
