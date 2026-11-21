import { eq } from "drizzle-orm";
import { deleteAccount } from "@/lib/account";
import { requireUser, verifyPassword } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { campusDto, selfUser, userStats } from "@/lib/dto";
import { ApiError, json, parseBody, route } from "@/lib/http";
import { updateUserInfo } from "@/lib/nexconn";
import { rateLimit } from "@/lib/rate-limit";
import { assertOwnUploads } from "@/lib/uploads";
import { deleteAccountSchema, profileUpdateSchema } from "@/lib/validation";

export const GET = route(async (req) => {
  const user = await requireUser(req);
  const [campus] = user.campusId
    ? await db().select().from(schema.campuses).where(eq(schema.campuses.id, user.campusId)).limit(1)
    : [];
  const stats = (await userStats([user.id])).get(user.id);
  return json({ user: selfUser(user), campus: campus ? campusDto(campus) : null, stats });
});

export const PATCH = route(async (req) => {
  const user = await requireUser(req);
  const body = await parseBody(req, profileUpdateSchema);
  const patch: Partial<typeof schema.users.$inferInsert> = {};
  if (body.name !== undefined) patch.name = body.name;
  if (body.hostel !== undefined) patch.hostel = body.hostel || null;
  if (body.department !== undefined) patch.department = body.department || null;
  if (body.avatarUrl) assertOwnUploads([body.avatarUrl], user.id, "avatars");
  if (body.avatarUrl !== undefined) patch.avatarUrl = body.avatarUrl;
  if (Object.keys(patch).length === 0) return json({ user: selfUser(user) });
  const [updated] = await db().update(schema.users).set(patch).where(eq(schema.users.id, user.id)).returning();
  if (updated && (patch.name !== undefined || patch.avatarUrl !== undefined)) {
    // Keep the Nexconn user profile (shown in pushes and SDK user info) in sync. Best effort.
    await updateUserInfo(updated.nexconnUserId, updated.name, updated.avatarUrl).catch((e) =>
      console.warn("[me] nexconn profile sync failed", e instanceof Error ? e.message : e),
    );
  }
  return json({ user: selfUser(updated ?? user) });
});

/** Deletes the signed-in student's account after they re-enter their password. */
export const DELETE = route(async (req) => {
  const user = await requireUser(req);
  await rateLimit(`delete-account:${user.id}`, 10, 900, "Too many attempts. Wait 15 minutes and try again.");
  const { password } = await parseBody(req, deleteAccountSchema);
  if (!(await verifyPassword(password, user.passwordHash))) {
    throw new ApiError(401, "invalid_password", "That password is incorrect.");
  }
  await deleteAccount(user);
  return json({ deleted: true });
});
