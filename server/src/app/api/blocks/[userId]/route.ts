import { and, eq } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { json, route, type RouteParams } from "@/lib/http";

export const DELETE = route(async (req, { params }: RouteParams<{ userId: string }>) => {
  const user = await requireUser(req);
  const { userId } = await params;
  await db().delete(schema.blocks).where(and(eq(schema.blocks.blockerId, user.id), eq(schema.blocks.blockedId, userId)));
  return json({ blocked: false });
});
