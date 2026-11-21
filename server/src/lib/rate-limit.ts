import { sql } from "drizzle-orm";
import { db } from "./db";
import { tooMany } from "./http";

/**
 * Fixed-window rate limiter backed by Postgres so it works across serverless instances.
 * Atomically increments the counter for `key`, resetting it when the window has elapsed.
 */
export async function rateLimit(key: string, limit: number, windowSeconds: number, message?: string): Promise<void> {
  const rows = await db().execute<{ count: number }>(sql`
    insert into rate_limits (key, window_start, count) values (${key}, now(), 1)
    on conflict (key) do update set
      count = case when rate_limits.window_start < now() - make_interval(secs => ${windowSeconds}) then 1 else rate_limits.count + 1 end,
      window_start = case when rate_limits.window_start < now() - make_interval(secs => ${windowSeconds}) then now() else rate_limits.window_start end
    returning count
  `);
  const count = Number(rows.rows[0]?.count ?? 0);
  if (count > limit) throw tooMany(message);
}

export function clientIp(req: Request): string {
  const fwd = req.headers.get("x-forwarded-for");
  return (fwd?.split(",")[0] ?? req.headers.get("x-real-ip") ?? "unknown").trim();
}
