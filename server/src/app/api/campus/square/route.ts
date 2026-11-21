import { eq } from "drizzle-orm";
import { requireCampusUser } from "@/lib/auth";
import { SUBCHANNELS } from "@/lib/campus";
import { db, schema } from "@/lib/db";
import { campusDto } from "@/lib/dto";
import { json, notFound, route } from "@/lib/http";
import { createSubchannel, isCommunityMember, joinCommunityChannel, listSubchannels } from "@/lib/nexconn";

/**
 * Campus Square bootstrap: makes sure the student is a member of their campus community channel
 * and that every standard subchannel exists (self-healing), then returns the subchannel list.
 */
export const GET = route(async (req) => {
  const user = await requireCampusUser(req);
  const [campus] = await db().select().from(schema.campuses).where(eq(schema.campuses.id, user.campusId)).limit(1);
  if (!campus) throw notFound("Your campus no longer exists. Pick another.");

  const [member, existing] = await Promise.all([
    isCommunityMember(user.nexconnUserId, campus.communityChannelId),
    listSubchannels(campus.communityChannelId),
  ]);
  if (!member) await joinCommunityChannel(user.nexconnUserId, campus.communityChannelId);
  const missing = SUBCHANNELS.filter((s) => !existing.includes(s.id));
  for (const sub of missing) await createSubchannel(campus.communityChannelId, sub.id);

  return json({
    campus: campusDto(campus),
    channelId: campus.communityChannelId,
    subchannels: SUBCHANNELS.map((s) => ({ id: s.id, name: s.name, description: s.description })),
  });
});
