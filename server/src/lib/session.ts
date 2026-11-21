import { eq } from "drizzle-orm";
import { issueSession } from "./auth";
import { db, schema } from "./db";
import type { User } from "./db/schema";
import { campusDto, selfUser } from "./dto";
import { env } from "./env";
import { issueAccessToken } from "./nexconn";

/** Everything the app needs right after sign-in: API session, Nexconn credentials, campus. */
export async function sessionPayload(user: User) {
  const [session, nexconnToken, campus] = await Promise.all([
    issueSession(user.id),
    issueAccessToken(user.nexconnUserId, user.name, user.avatarUrl),
    user.campusId
      ? db().select().from(schema.campuses).where(eq(schema.campuses.id, user.campusId)).limit(1).then((r) => r[0] ?? null)
      : Promise.resolve(null),
  ]);
  return {
    token: session.token,
    expiresAt: session.expiresAt,
    user: selfUser(user),
    campus: campus ? campusDto(campus) : null,
    nexconn: { appKey: env.nexconnAppKey(), userId: user.nexconnUserId, token: nexconnToken },
  };
}
