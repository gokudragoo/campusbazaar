import { inArray } from "drizzle-orm";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { publicUser } from "@/lib/dto";
import { json, parseBody, route } from "@/lib/http";

const bodySchema = z.object({ nexconnUserIds: z.array(z.string().min(1).max(64)).min(1).max(100) });

/** Resolves Nexconn user ids (chat peers, Campus Square senders) to public profiles. */
export const POST = route(async (req) => {
  await requireUser(req);
  const { nexconnUserIds } = await parseBody(req, bodySchema);
  const rows = await db().select().from(schema.users).where(inArray(schema.users.nexconnUserId, [...new Set(nexconnUserIds)]));
  return json({ users: rows.map(publicUser) });
});
