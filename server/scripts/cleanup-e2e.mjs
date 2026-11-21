#!/usr/bin/env node
/**
 * Removes e2e / demo test data (accounts with @campusbazaar.test emails) from the database and
 * dismisses Nexconn community channels of campuses that only test accounts belonged to.
 * Reads DATABASE_URL_UNPOOLED and Nexconn credentials from server/.env.local.
 *
 *   node scripts/cleanup-e2e.mjs
 */
import { createHash, randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { neon } from "@neondatabase/serverless";

function loadEnv() {
  const path = fileURLToPath(new URL("../.env.local", import.meta.url));
  const out = {};
  for (const line of readFileSync(path, "utf8").split(/\r?\n/)) {
    const i = line.indexOf("=");
    if (i <= 0 || line.startsWith("#")) continue;
    let v = line.slice(i + 1);
    if (v.startsWith('"') && v.endsWith('"')) v = v.slice(1, -1);
    out[line.slice(0, i)] = v;
  }
  return out;
}

async function dismissCommunity(env, channelId) {
  const nonce = randomBytes(6).toString("hex");
  const ts = Date.now().toString();
  const sig = createHash("sha1").update(env.NEXCONN_APP_SECRET + nonce + ts).digest("hex");
  const res = await fetch(`${env.NEXCONN_API_BASE}/v4/community-channel/dismiss`, {
    method: "POST",
    headers: { "App-Key": env.NEXCONN_APP_KEY, Nonce: nonce, Timestamp: ts, Signature: sig, "Content-Type": "application/json" },
    body: JSON.stringify({ channelId }),
  });
  return res.ok;
}

export async function cleanup() {
  const env = loadEnv();
  const sql = neon(env.DATABASE_URL_UNPOOLED ?? env.DATABASE_URL);
  const users = await sql`select id from users where email like '%@campusbazaar.test'`;
  const ids = users.map((u) => u.id);
  if (ids.length === 0) {
    console.log("cleanup: nothing to remove");
    return;
  }
  const campuses = await sql`
    select c.id, c.community_channel_id from campuses c
    where c.created_by_id = any(${ids})
      and not exists (select 1 from users u where u.campus_id = c.id and not (u.id = any(${ids})))`;
  const campusIds = campuses.map((c) => c.id);

  await sql`delete from ratings where rater_id = any(${ids}) or ratee_id = any(${ids})`;
  await sql`delete from offers where buyer_id = any(${ids}) or seller_id = any(${ids})`;
  await sql`delete from meetups where buyer_id = any(${ids}) or seller_id = any(${ids})`;
  await sql`delete from threads where buyer_id = any(${ids}) or seller_id = any(${ids})`;
  await sql`delete from wanted_responses where responder_id = any(${ids})`;
  await sql`delete from wanted_posts where user_id = any(${ids})`;
  await sql`delete from reports where reporter_id = any(${ids}) or target_user_id = any(${ids})`;
  await sql`delete from reports where listing_id in (select id from listings where seller_id = any(${ids}))`;
  await sql`delete from saved_listings where user_id = any(${ids}) or listing_id in (select id from listings where seller_id = any(${ids}))`;
  await sql`delete from hidden_listings where user_id = any(${ids}) or listing_id in (select id from listings where seller_id = any(${ids}))`;
  await sql`delete from listings where seller_id = any(${ids})`;
  await sql`delete from blocks where blocker_id = any(${ids}) or blocked_id = any(${ids})`;
  await sql`update users set campus_id = null where id = any(${ids})`;
  if (campusIds.length) await sql`delete from campuses where id = any(${campusIds})`;
  await sql`delete from users where id = any(${ids})`;
  for (const c of campuses) await dismissCommunity(env, c.community_channel_id).catch(() => false);
  console.log(`cleanup: removed ${ids.length} test users and ${campusIds.length} test campuses`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  cleanup().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
