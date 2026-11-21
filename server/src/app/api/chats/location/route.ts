import { eq } from "drizzle-orm";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { threadsBetween } from "@/lib/deals";
import { forbidden, json, notFound, parseBody, route } from "@/lib/http";
import { isBlockedEitherWay } from "@/lib/listing-access";
import { sendDirectMessage } from "@/lib/nexconn";
import { rateLimit } from "@/lib/rate-limit";

const bodySchema = z.object({
  peerNexconnUserId: z.string().min(1).max(64),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  poi: z.string().trim().min(1).max(80),
  thumbnailBase64: z.string().max(12000).regex(/^[A-Za-z0-9+/=]*$/).default(""),
});

/**
 * Relays a Nexconn location message (RC:LBSMsg) between two students who are negotiating.
 * The Android SDK rejects client-side location sends on this app key (error 31610), so the app
 * falls back to this route, which sends the same built-in message type through the Platform Chat
 * API as the student, echoed back to their own devices.
 */
export const POST = route(async (req) => {
  const user = await requireUser(req);
  await rateLimit(`location:${user.id}`, 30, 3600);
  const body = await parseBody(req, bodySchema);
  const [peer] = await db().select().from(schema.users).where(eq(schema.users.nexconnUserId, body.peerNexconnUserId)).limit(1);
  if (!peer) throw notFound("This student's account no longer exists.");
  if ((await threadsBetween(user.id, peer.id)).length === 0) throw forbidden("You can share a location once you're chatting about an item.");
  if (await isBlockedEitherWay(user.id, peer.id)) throw forbidden("You can't message this student.");

  const messageId = await sendDirectMessage({
    fromUserId: user.nexconnUserId,
    toUserId: peer.nexconnUserId,
    messageType: "RC:LBSMsg",
    content: { content: body.thumbnailBase64, latitude: body.latitude, longitude: body.longitude, poi: body.poi },
    pushContent: `${user.name} shared a location`,
  });
  return json({ messageId }, { status: 201 });
});
