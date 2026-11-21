import { desc, eq } from "drizzle-orm";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { publicUser, usersById } from "@/lib/dto";
import { badRequest, json, notFound, parseBody, route } from "@/lib/http";

/**
 * Server-side block record. The app also adds the user to the Nexconn blocklist through the SDK,
 * which stops their direct messages; this record hides listings and wanted posts both ways.
 */
export const GET = route(async (req) => {
  const user = await requireUser(req);
  const rows = await db().select().from(schema.blocks).where(eq(schema.blocks.blockerId, user.id)).orderBy(desc(schema.blocks.createdAt));
  const users = await usersById(rows.map((r) => r.blockedId));
  return json({ blocked: rows.map((r) => users.get(r.blockedId)).filter((u) => u !== undefined).map((u) => publicUser(u)) });
});

export const POST = route(async (req) => {
  const user = await requireUser(req);
  const { userId } = await parseBody(req, z.object({ userId: z.string().min(1).max(40) }));
  if (userId === user.id) throw badRequest("You can't block yourself.");
  const target = (await usersById([userId])).get(userId);
  if (!target) throw notFound("This student's account no longer exists.");
  await db().insert(schema.blocks).values({ blockerId: user.id, blockedId: userId }).onConflictDoNothing();
  return json({ blocked: true, user: publicUser(target) }, { status: 201 });
});
