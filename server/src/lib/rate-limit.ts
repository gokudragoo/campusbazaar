import { sql } from "drizzle-orm";
import { db } from "./db";
import { tooMany } from "./http";

/**
 * Fixed-window counters backed by Postgres so they work across serverless instances.
 * Atomically increments the counter for `key`, resetting it when the window has elapsed, and
 * returns the count in the current window.
 */
export async function recordAttempt(key: string, windowSeconds: number): Promise<number> {
  const rows = await db().execute<{ count: number }>(sql`
    insert into rate_limits (key, window_start, count) values (${key}, now(), 1)
    on conflict (key) do update set
      count = case when rate_limits.window_start < now() - make_interval(secs => ${windowSeconds}) then 1 else rate_limits.count + 1 end,
      window_start = case when rate_limits.window_start < now() - make_interval(secs => ${windowSeconds}) then now() else rate_limits.window_start end
    returning count
  `);
  return Number(rows.rows[0]?.count ?? 0);
}

/** Counts this request against `key` and throws 429 once it goes over `limit` in the window. */
export async function rateLimit(key: string, limit: number, windowSeconds: number, message?: string): Promise<void> {
  if ((await recordAttempt(key, windowSeconds)) > limit) throw tooMany(message);
}

/** Throws 429 if `key` has already reached `limit` in its current window, without counting this request. */
export async function assertUnderLimit(key: string, limit: number, windowSeconds: number, message?: string): Promise<void> {
  const rows = await db().execute<{ count: number }>(sql`
    select count from rate_limits
    where key = ${key} and window_start >= now() - make_interval(secs => ${windowSeconds})
  `);
  if (Number(rows.rows[0]?.count ?? 0) >= limit) throw tooMany(message);
}

export function clientIp(req: Request): string {
  const fwd = req.headers.get("x-forwarded-for");
  return (fwd?.split(",")[0] ?? req.headers.get("x-real-ip") ?? "unknown").trim();
}
