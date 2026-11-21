import { and, eq } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { usersById } from "@/lib/dto";
import { json, route, type RouteParams } from "@/lib/http";
import { removeFromBlocklist } from "@/lib/nexconn";

/** Unblocks a student: removes the server record and takes them off the Nexconn blocklist. */
export const DELETE = route(async (req, { params }: RouteParams<{ userId: string }>) => {
  const user = await requireUser(req);
  const { userId } = await params;
  await db().delete(schema.blocks).where(and(eq(schema.blocks.blockerId, user.id), eq(schema.blocks.blockedId, userId)));
  const target = (await usersById([userId])).get(userId);
  if (target) await removeFromBlocklist(user.nexconnUserId, [target.nexconnUserId]);
  return json({ blocked: false });
});
