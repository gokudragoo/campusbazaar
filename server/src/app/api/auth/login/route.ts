import { and, eq, isNull } from "drizzle-orm";
import { verifyPassword } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { ApiError, json, parseBody, route } from "@/lib/http";
import { assertUnderLimit, clientIp, rateLimit, recordAttempt } from "@/lib/rate-limit";
import { sessionPayload } from "@/lib/session";
import { loginSchema } from "@/lib/validation";

const WINDOW = 900;
const TOO_MANY = "Too many failed sign-in attempts. Wait 15 minutes and try again.";

export const POST = route(async (req) => {
  const body = await parseBody(req, loginSchema);
  const ip = clientIp(req);
  await rateLimit(`login:${ip}`, 30, WINDOW, "Too many sign-in attempts. Wait 15 minutes and try again.");
  // Only failed attempts count against an account, per network and (more loosely) overall, so a
  // stranger can't lock a student out by typing wrong passwords from somewhere else.
  const perNetwork = `login-fail:${ip}:${body.email}`;
  const perAccount = `login-fail:${body.email}`;
  await assertUnderLimit(perNetwork, 10, WINDOW, TOO_MANY);
  await assertUnderLimit(perAccount, 50, WINDOW, TOO_MANY);

  const [user] = await db()
    .select()
    .from(schema.users)
    .where(and(eq(schema.users.email, body.email), isNull(schema.users.deletedAt)))
    .limit(1);
  // Same message for unknown email and wrong password, so accounts can't be enumerated.
  if (!user || !(await verifyPassword(body.password, user.passwordHash))) {
    await Promise.all([recordAttempt(perNetwork, WINDOW), recordAttempt(perAccount, WINDOW)]);
    throw new ApiError(401, "invalid_credentials", "Email or password is incorrect.");
  }
  return json(await sessionPayload(user));
});
