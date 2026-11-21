import { eq } from "drizzle-orm";
import { requireCampusUser } from "@/lib/auth";
import { SUBCHANNELS } from "@/lib/campus";
import { ensureCampusCommunity } from "@/lib/campus-membership";
import { db, schema } from "@/lib/db";
import { campusDto } from "@/lib/dto";
import { json, notFound, route } from "@/lib/http";
import { isCommunityMember, joinCommunityChannel } from "@/lib/nexconn";

/**
 * Campus Square bootstrap: makes sure the campus community channel and every standard
 * subchannel exist (self-healing, also after an App Key change) and that the student is a
 * member, then returns the subchannel list.
 */
export const GET = route(async (req) => {
  const user = await requireCampusUser(req);
  const [campus] = await db().select().from(schema.campuses).where(eq(schema.campuses.id, user.campusId)).limit(1);
  if (!campus) throw notFound("Your campus no longer exists. Pick another.");

  // When the channel is missing, ensureCampusCommunity creates it with this student as its first member.
  const [, member] = await Promise.all([
    ensureCampusCommunity(campus, user.nexconnUserId),
    isCommunityMember(user.nexconnUserId, campus.communityChannelId),
  ]);
  if (!member) await joinCommunityChannel(user.nexconnUserId, campus.communityChannelId);

  return json({
    campus: campusDto(campus),
    channelId: campus.communityChannelId,
    subchannels: SUBCHANNELS.map((s) => ({ id: s.id, name: s.name, description: s.description })),
  });
});
