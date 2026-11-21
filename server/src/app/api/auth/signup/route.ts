import { eq } from "drizzle-orm";
import { hashPassword } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { conflict, json, parseBody, route } from "@/lib/http";
import { nexconnId, randomId } from "@/lib/ids";
import { clientIp, rateLimit } from "@/lib/rate-limit";
import { sessionPayload } from "@/lib/session";
import { signupSchema } from "@/lib/validation";

export const POST = route(async (req) => {
  await rateLimit(`signup:${clientIp(req)}`, 10, 3600, "Too many sign-ups from this network. Try again later.");
  const body = await parseBody(req, signupSchema);
  const [existing] = await db().select({ id: schema.users.id }).from(schema.users).where(eq(schema.users.email, body.email)).limit(1);
  if (existing) throw conflict("An account with this email already exists. Sign in instead.", "email_taken");

  const [user] = await db()
    .insert(schema.users)
    .values({
      id: randomId(16),
      nexconnUserId: nexconnId("u"),
      name: body.name,
      email: body.email,
      passwordHash: await hashPassword(body.password),
    })
    .onConflictDoNothing()
    .returning();
  if (!user) throw conflict("An account with this email already exists. Sign in instead.", "email_taken");
  return json(await sessionPayload(user), { status: 201 });
});
