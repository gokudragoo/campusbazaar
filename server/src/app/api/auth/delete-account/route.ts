import { and, eq, isNull } from "drizzle-orm";
import { verifyPassword } from "@/lib/auth";
import { deleteAccount } from "@/lib/account";
import { db, schema } from "@/lib/db";
import { ApiError, json, parseBody, route } from "@/lib/http";
import { assertUnderLimit, clientIp, rateLimit, recordAttempt } from "@/lib/rate-limit";
import { loginSchema } from "@/lib/validation";

const WINDOW = 900;
const TOO_MANY = "Too many failed attempts. Wait 15 minutes and try again.";

/** Account deletion from the website, for students who no longer have the app. */
export const POST = route(async (req) => {
  const body = await parseBody(req, loginSchema);
  const ip = clientIp(req);
  await rateLimit(`delete-account:${ip}`, 20, WINDOW, TOO_MANY);
  const failures = `login-fail:${ip}:${body.email}`;
  await assertUnderLimit(failures, 10, WINDOW, TOO_MANY);

  const [user] = await db()
    .select()
    .from(schema.users)
    .where(and(eq(schema.users.email, body.email), isNull(schema.users.deletedAt)))
    .limit(1);
  if (!user || !(await verifyPassword(body.password, user.passwordHash))) {
    await recordAttempt(failures, WINDOW);
    throw new ApiError(401, "invalid_credentials", "Email or password is incorrect.");
  }
  await deleteAccount(user);
  return json({ deleted: true });
});
