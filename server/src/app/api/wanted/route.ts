import { and, desc, eq, inArray, notInArray, type SQL } from "drizzle-orm";
import { z } from "zod";
import { requireCampusUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { blockedUserIds, usersById, wantedDto } from "@/lib/dto";
import { json, parseBody, parseQuery, route } from "@/lib/http";
import { randomId } from "@/lib/ids";
import { rateLimit } from "@/lib/rate-limit";
import { categorySchema, wantedCreateSchema } from "@/lib/validation";

const querySchema = z.object({
  category: categorySchema.optional(),
  mine: z.enum(["1", "0"]).optional(),
  cursor: z.string().max(20).optional(),
});

/** Wanted board for my campus: open requests newest first (or my own posts, any state). */
export const GET = route(async (req) => {
  const user = await requireCampusUser(req);
  const q = parseQuery(req, querySchema);
  const W = schema.wantedPosts;
  const where: SQL[] = [eq(W.campusId, user.campusId)];
  if (q.mine === "1") {
    where.push(eq(W.userId, user.id));
  } else {
    where.push(eq(W.status, "open"));
    const blocked = await blockedUserIds(user.id);
    if (blocked.length) where.push(notInArray(W.userId, blocked));
  }
  if (q.category) where.push(eq(W.category, q.category));
  const offset = Math.max(0, Number.parseInt(q.cursor ?? "0", 10) || 0);
  const rows = await db()
    .select()
    .from(W)
    .where(and(...where))
    .orderBy(desc(W.createdAt))
    .limit(31)
    .offset(offset);
  const page = rows.slice(0, 30);
  const authors = await usersById(page.map((w) => w.userId));
  const responded = page.length
    ? await db()
        .select({ id: schema.wantedResponses.wantedId })
        .from(schema.wantedResponses)
        .where(and(eq(schema.wantedResponses.responderId, user.id), inArray(schema.wantedResponses.wantedId, page.map((w) => w.id))))
    : [];
  const respondedSet = new Set(responded.map((r) => r.id));
  return json({
    wanted: page.map((w) => ({ ...wantedDto(w, authors.get(w.userId)), respondedByMe: respondedSet.has(w.id) })),
    nextCursor: rows.length > 30 ? String(offset + 30) : null,
  });
});

export const POST = route(async (req) => {
  const user = await requireCampusUser(req);
  await rateLimit(`wanted:${user.id}`, 10, 86400, "You've posted several requests today. Try again tomorrow.");
  const body = await parseBody(req, wantedCreateSchema);
  const [post] = await db()
    .insert(schema.wantedPosts)
    .values({
      id: randomId(14),
      campusId: user.campusId,
      userId: user.id,
      title: body.title,
      description: body.description,
      category: body.category,
      budget: body.budget ?? null,
    })
    .returning();
  return json({ wanted: wantedDto(post!, user) }, { status: 201 });
});
