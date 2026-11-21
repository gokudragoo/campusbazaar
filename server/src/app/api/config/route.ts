import { SUBCHANNELS } from "@/lib/campus";
import { CATEGORIES, CONDITIONS } from "@/lib/db/schema";
import { env } from "@/lib/env";
import { json, route } from "@/lib/http";
import { botUserId } from "@/lib/ids";

/** Public client configuration. Exposes only the Nexconn App Key, never the secret. */
export const GET = route(async () => {
  return json(
    {
      nexconnAppKey: env.nexconnAppKey(),
      nexconnArea: "SG",
      apkUrl: env.apkUrl(),
      categories: CATEGORIES,
      conditions: CONDITIONS,
      subchannels: SUBCHANNELS,
      aiEnabled: env.geminiApiKey() !== null,
      /** Nexconn user id of the bot that sends system-channel notices (Activity inbox). */
      noticeSenderId: botUserId(),
    },
    { headers: { "Cache-Control": "public, max-age=300" } },
  );
});
