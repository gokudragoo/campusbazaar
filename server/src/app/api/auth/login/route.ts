import { eq } from "drizzle-orm";
import { verifyPassword } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { ApiError, json, parseBody, route } from "@/lib/http";
import { clientIp, rateLimit } from "@/lib/rate-limit";
import { sessionPayload } from "@/lib/session";
import { loginSchema } from "@/lib/validation";

export const POST = route(async (req) => {
  const body = await parseBody(req, loginSchema);
  await rateLimit(`login:${clientIp(req)}`, 30, 900, "Too many sign-in attempts. Wait 15 minutes and try again.");
  await rateLimit(`login-email:${body.email}`, 10, 900, "Too many sign-in attempts for this account. Wait 15 minutes and try again.");
  const [user] = await db().select().from(schema.users).where(eq(schema.users.email, body.email)).limit(1);
  // Same message for unknown email and wrong password, so accounts can't be enumerated.
  if (!user || !(await verifyPassword(body.password, user.passwordHash))) {
    throw new ApiError(401, "invalid_credentials", "Email or password is incorrect.");
  }
  return json(await sessionPayload(user));
});
