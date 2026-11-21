import { eq } from "drizzle-orm";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { wantedDto } from "@/lib/dto";
import { forbidden, json, notFound, parseBody, route, type RouteParams } from "@/lib/http";

type P = RouteParams<{ id: string }>;

async function ownPost(id: string, userId: string) {
  const [post] = await db().select().from(schema.wantedPosts).where(eq(schema.wantedPosts.id, id)).limit(1);
  if (!post) throw notFound("This request no longer exists.");
  if (post.userId !== userId) throw forbidden("Only the student who posted this can change it.");
  return post;
}

/** Close or reopen my wanted post. */
export const PATCH = route(async (req, { params }: P) => {
  const user = await requireUser(req);
  const post = await ownPost((await params).id, user.id);
  const { status } = await parseBody(req, z.object({ status: z.enum(["open", "closed"]) }));
  const [updated] = await db().update(schema.wantedPosts).set({ status }).where(eq(schema.wantedPosts.id, post.id)).returning();
  return json({ wanted: wantedDto(updated!) });
});

export const DELETE = route(async (req, { params }: P) => {
  const user = await requireUser(req);
  const post = await ownPost((await params).id, user.id);
  await db().delete(schema.wantedPosts).where(eq(schema.wantedPosts.id, post.id));
  return json({ ok: true });
});
