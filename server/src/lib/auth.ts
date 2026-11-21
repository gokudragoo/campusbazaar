import bcrypt from "bcryptjs";
import { eq } from "drizzle-orm";
import { jwtVerify, SignJWT } from "jose";
import { db, schema } from "./db";
import { env } from "./env";
import { unauthorized } from "./http";
import type { User } from "./db/schema";

const ISSUER = "campusbazaar";
const AUDIENCE = "campusbazaar-app";
const SESSION_DAYS = 30;

const secretKey = () => new TextEncoder().encode(env.jwtSecret());

export async function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(password, 10);
}

export async function verifyPassword(password: string, hash: string): Promise<boolean> {
  return bcrypt.compare(password, hash);
}

export async function issueSession(userId: string): Promise<{ token: string; expiresAt: string }> {
  const expires = new Date(Date.now() + SESSION_DAYS * 24 * 60 * 60 * 1000);
  const token = await new SignJWT({})
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(userId)
    .setIssuer(ISSUER)
    .setAudience(AUDIENCE)
    .setIssuedAt()
    .setExpirationTime(expires)
    .sign(secretKey());
  return { token, expiresAt: expires.toISOString() };
}

export async function verifySession(token: string): Promise<string | null> {
  try {
    const { payload } = await jwtVerify(token, secretKey(), { issuer: ISSUER, audience: AUDIENCE });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

/** Resolves the signed-in user from the `Authorization: Bearer` header or throws 401. */
export async function requireUser(req: Request): Promise<User> {
  const header = req.headers.get("authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match?.[1]) throw unauthorized();
  const userId = await verifySession(match[1].trim());
  if (!userId) throw unauthorized("Your session has expired. Sign in again.");
  const [user] = await db().select().from(schema.users).where(eq(schema.users.id, userId)).limit(1);
  if (!user || user.deletedAt) throw unauthorized("Your account no longer exists.");
  return user;
}

/** Same as requireUser, but also insists the user has picked a campus. */
export async function requireCampusUser(req: Request): Promise<User & { campusId: string }> {
  const user = await requireUser(req);
  if (!user.campusId) {
    const { ApiError } = await import("./http");
    throw new ApiError(409, "campus_required", "Pick your campus first.");
  }
  return user as User & { campusId: string };
}
