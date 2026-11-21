import { requireUser } from "@/lib/auth";
import { env } from "@/lib/env";
import { json, route } from "@/lib/http";
import { issueAccessToken } from "@/lib/nexconn";
import { rateLimit } from "@/lib/rate-limit";

/** Re-issues a Nexconn access token. The app calls this when connect() reports an invalid/expired token. */
export const POST = route(async (req) => {
  const user = await requireUser(req);
  await rateLimit(`nxtoken:${user.id}`, 20, 3600);
  const token = await issueAccessToken(user.nexconnUserId, user.name, user.avatarUrl);
  return json({ appKey: env.nexconnAppKey(), userId: user.nexconnUserId, token });
});
