#!/usr/bin/env node
/**
 * End-to-end smoke test against a real deployment (real Neon, Nexconn, Blob and Gemini).
 *
 *   node scripts/e2e.mjs https://campusbazaar-api.vercel.app path/to/item-photo.jpg [--keep]
 *
 * Drives two students through the whole loop: sign up, add + join a campus, Gemini drafts a
 * listing from a real photo, B finds it, chats, offers, A counters, B accepts (listing reserved),
 * meetup proposed + confirmed, A marks sold, both rate, wanted post + response, Campus Square,
 * safety endpoints. Test accounts use @campusbazaar.test emails; cleanup removes them unless --keep.
 */
import { readFile } from "node:fs/promises";
import { basename } from "node:path";

const [base, photoPath] = process.argv.slice(2).filter((a) => !a.startsWith("--"));
const KEEP = process.argv.includes("--keep");
if (!base || !photoPath) {
  console.error("usage: node scripts/e2e.mjs <baseUrl> <photo.jpg> [--keep]");
  process.exit(2);
}

let passed = 0;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function check(cond, label, extra) {
  if (!cond) {
    console.error(`  FAIL ${label}`, extra ?? "");
    throw new Error(`Assertion failed: ${label}`);
  }
  passed++;
  console.log(`  ok   ${label}`);
}

async function api(method, path, { token, body, form, expect = [200, 201] } = {}) {
  let lastErr;
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      const res = await fetch(`${base}${path}`, {
        method,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(body ? { "Content-Type": "application/json" } : {}),
        },
        body: form ?? (body ? JSON.stringify(body) : undefined),
        signal: AbortSignal.timeout(60000),
      });
      const text = await res.text();
      const data = text ? JSON.parse(text) : {};
      if (!expect.includes(res.status)) {
        throw Object.assign(new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 300)}`), { status: res.status, data });
      }
      return data;
    } catch (err) {
      lastErr = err;
      // Retry only transport errors and 5xx; assertion-worthy 4xx bubble up immediately.
      if (err.status && err.status < 500) throw err;
      await sleep(800 * 2 ** attempt);
    }
  }
  throw lastErr;
}

const stamp = Date.now().toString(36);
const email = (who) => `e2e-${who}-${stamp}@campusbazaar.test`;

async function main() {
  console.log(`E2E against ${base}`);

  console.log("1. Health and config");
  const health = await api("GET", "/api/health");
  check(health.ok === true, "health ok");
  const config = await api("GET", "/api/config");
  check(typeof config.nexconnAppKey === "string" && !("nexconnAppSecret" in config), "config exposes app key only");
  check(config.subchannels.length === 5, "five Campus Square subchannels configured");

  console.log("2. Sign up two students");
  const a = await api("POST", "/api/auth/signup", { body: { name: "Asha (E2E seller)", email: email("a"), password: "campus-pass-1" } });
  const b = await api("POST", "/api/auth/signup", { body: { name: "Rohan (E2E buyer)", email: email("b"), password: "campus-pass-2" } });
  check(a.token && a.nexconn.token && a.nexconn.userId.startsWith("cbz"), "A has session + Nexconn token with cbz prefix");
  check(b.user.email === email("b") && b.campus === null, "B starts without a campus");
  const dup = await api("POST", "/api/auth/signup", { body: { name: "Dup", email: email("a"), password: "campus-pass-1" }, expect: [409] });
  check(dup.error.code === "email_taken", "duplicate email rejected");
  const badLogin = await api("POST", "/api/auth/login", { body: { email: email("a"), password: "wrong-pass" }, expect: [401] });
  check(badLogin.error.code === "invalid_credentials", "wrong password rejected");
  const login = await api("POST", "/api/auth/login", { body: { email: email("a").toUpperCase(), password: "campus-pass-1" } });
  check(login.user.id === a.user.id, "login is case-insensitive on email");
  await api("GET", "/api/me", { expect: [401] });
  check(true, "unauthenticated /api/me is 401");

  console.log("3. Campus: add, dedupe, join");
  const campusName = `E2E Test Institute ${stamp}`;
  const created = await api("POST", "/api/campuses", { token: a.token, body: { name: campusName, city: "Testpur" } });
  check(created.created === true && created.campus.communityChannelId.startsWith("cbz"), "campus created with Nexconn community channel");
  const again = await api("POST", "/api/campuses", { token: b.token, body: { name: campusName.toUpperCase().replace(/ /g, "  "), city: "testpur." } });
  check(again.created === false && again.campus.id === created.campus.id, "dedupe returns the same campus and joins B");
  const search = await api("GET", `/api/campuses?q=${encodeURIComponent(stamp)}`, { token: b.token });
  check(search.campuses.some((c) => c.id === created.campus.id && c.memberCount === 2), "campus search finds it with 2 members");

  console.log("4. Campus Square (Nexconn community channel + subchannels)");
  const squareA = await api("GET", "/api/campus/square", { token: a.token });
  const squareB = await api("GET", "/api/campus/square", { token: b.token });
  check(squareA.channelId === squareB.channelId && squareB.subchannels.map((s) => s.id).join() === "general,books,electronics,ridestrips,lostfound", "both members see the same 5 subchannels");

  console.log("5. Sell: upload real photo, Gemini vision draft");
  const photo = await readFile(photoPath);
  const form = new FormData();
  form.append("file", new Blob([photo], { type: "image/jpeg" }), basename(photoPath));
  const upload = await api("POST", "/api/uploads", { token: a.token, form });
  check(upload.url.includes(".public.blob.vercel-storage.com/cbz/listings/"), "photo stored in Blob");
  const badForm = new FormData();
  badForm.append("file", new Blob([Buffer.from("not an image")], { type: "image/jpeg" }), "fake.jpg");
  await api("POST", "/api/uploads", { token: a.token, form: badForm, expect: [400] });
  check(true, "non-image upload rejected");

  const t0 = Date.now();
  const { suggestion } = await api("POST", "/api/ai/listing-suggest", { token: a.token, body: { imageUrls: [upload.url] } });
  console.log(`       Gemini (${suggestion.model}, ${Date.now() - t0} ms):`, JSON.stringify(suggestion));
  check(suggestion.title.length >= 3 && config.categories.includes(suggestion.category), "AI suggested a title and a valid category");
  check(suggestion.priceMin > 0 && suggestion.priceMax >= suggestion.priceMin, "AI suggested a positive ₹ price range");
  const notMine = await api("POST", "/api/ai/listing-suggest", { token: b.token, body: { imageUrls: [upload.url] }, expect: [400] });
  check(notMine.error.message.includes("uploaded"), "cannot analyse another student's photos");

  const price = Math.round((suggestion.priceMin + suggestion.priceMax) / 2);
  const { listing } = await api("POST", "/api/listings", {
    token: a.token,
    body: {
      title: suggestion.title,
      description: suggestion.description,
      category: suggestion.category,
      condition: suggestion.condition,
      price,
      negotiable: true,
      meetupSpot: "Central library steps",
      photos: [upload.url],
      aiAssisted: true,
      aiPriceMin: suggestion.priceMin,
      aiPriceMax: suggestion.priceMax,
    },
  });
  check(listing.status === "active" && listing.aiAssisted, "listing published from the AI draft");
  const foreign = await api("POST", "/api/listings", {
    token: a.token,
    body: { title: "Tracking pixel", category: "other", condition: "good", price: 10, photos: ["https://example.com/x.jpg"] },
    expect: [400],
  });
  check(foreign.error.code === "invalid_photo", "listing photos must be the seller's own uploads");

  console.log("6. Browse and find");
  const feed = await api("GET", "/api/listings", { token: b.token });
  check(feed.listings.some((l) => l.id === listing.id), "B sees it in the campus feed");
  const word = suggestion.title.split(/\s+/).find((w) => w.length >= 3) ?? suggestion.title;
  const found = await api("GET", `/api/listings?q=${encodeURIComponent(word)}&category=${suggestion.category}&maxPrice=${price}`, { token: b.token });
  check(found.listings.some((l) => l.id === listing.id), `search "${word}" + category + max price finds it`);
  const tooCheap = await api("GET", `/api/listings?maxPrice=${Math.max(0, price - 1)}`, { token: b.token });
  check(!tooCheap.listings.some((l) => l.id === listing.id), "price filter excludes it below its price");
  await api("POST", `/api/listings/${listing.id}/save`, { token: b.token });
  const saved = await api("GET", "/api/me/saved", { token: b.token });
  check(saved.listings[0]?.id === listing.id, "B saved the item");
  const detail = await api("GET", `/api/listings/${listing.id}`, { token: b.token });
  check(detail.listing.seller.id === a.user.id && detail.listing.saved === true, "detail shows seller card and saved state");

  console.log("7. Chat and negotiate");
  const chat = await api("POST", "/api/chats/start", { token: b.token, body: { listingId: listing.id } });
  check(chat.created && chat.peer.nexconnUserId === a.nexconn.userId, "chat thread opened with seller's Nexconn id");
  const ownChat = await api("POST", "/api/chats/start", { token: a.token, body: { listingId: listing.id }, expect: [400] });
  check(Boolean(ownChat.error), "seller cannot chat with themselves");
  const offer1 = await api("POST", "/api/offers", { token: b.token, body: { listingId: listing.id, amount: Math.max(1, price - 150) } });
  check(offer1.offer.status === "pending" && offer1.offer.fromUserId === b.user.id, "B made an offer");
  await api("POST", `/api/offers/${offer1.offer.id}/message`, { token: b.token, body: { messageId: "E2E1-TEST-MSG1-0001" } });
  const selfAccept = await api("POST", `/api/offers/${offer1.offer.id}/accept`, { token: b.token, expect: [403] });
  check(Boolean(selfAccept.error), "B cannot accept their own offer");
  const counter = await api("POST", `/api/offers/${offer1.offer.id}/counter`, { token: a.token, body: { amount: price - 50 } });
  check(counter.countered.status === "countered" && counter.offer.fromUserId === a.user.id, "A countered");
  const accepted = await api("POST", `/api/offers/${counter.offer.id}/accept`, { token: b.token });
  check(accepted.offer.status === "accepted" && accepted.listing.status === "reserved" && accepted.listing.reservedForId === b.user.id, "B accepted; listing reserved for B");
  const late = await api("POST", `/api/offers/${offer1.offer.id}/accept`, { token: a.token, expect: [409] });
  check(late.error.code === "offer_closed", "superseded offer can't be accepted");

  const when = new Date(Date.now() + 26 * 3600 * 1000).toISOString();
  const meetup = await api("POST", "/api/meetups", {
    token: a.token,
    body: { threadId: chat.threadId, spot: "Central library steps", scheduledAt: when, lat: 28.5459, lng: 77.1926, note: "Bring exact change" },
  });
  check(meetup.meetup.status === "proposed", "A proposed a meetup");
  const confirm = await api("POST", `/api/meetups/${meetup.meetup.id}/confirm`, { token: b.token });
  check(confirm.meetup.status === "confirmed", "B confirmed the meetup");
  const peerState = await api("GET", `/api/chats/peer/${a.nexconn.userId}`, { token: b.token });
  check(peerState.threads[0].offers.length === 2 && peerState.threads[0].meetups[0].status === "confirmed", "deal state visible to chat screen");
  await api("POST", "/api/chats/replied", { token: a.token, body: { peerNexconnUserId: b.nexconn.userId } });
  const threadsA = await api("GET", "/api/chats/threads", { token: a.token });
  check(threadsA.threads.some((t) => t.id === chat.threadId && t.role === "seller"), "seller sees the thread in their chat list");

  console.log("8. Sold and ratings");
  const early = await api("POST", "/api/ratings", { token: b.token, body: { listingId: listing.id, stars: 5 }, expect: [409] });
  check(Boolean(early.error), "can't rate before sale");
  const sold = await api("POST", `/api/listings/${listing.id}/sold`, { token: a.token, body: { buyerId: b.user.id } });
  check(sold.listing.status === "sold" && sold.listing.soldToId === b.user.id, "A marked it sold to B");
  await api("POST", "/api/ratings", { token: b.token, body: { listingId: listing.id, stars: 5, comment: "Exactly as described, on time." } });
  await api("POST", "/api/ratings", { token: a.token, body: { listingId: listing.id, stars: 4, comment: "Smooth deal." } });
  const twice = await api("POST", "/api/ratings", { token: a.token, body: { listingId: listing.id, stars: 1 }, expect: [409] });
  check(twice.error.code === "already_rated", "both rated once, no double rating");
  const profileA = await api("GET", `/api/users/${a.user.id}`, { token: b.token });
  check(profileA.user.ratingAvg === 5 && profileA.user.soldCount === 1, "A's profile shows 5.0 rating and 1 sold");
  const feedAfter = await api("GET", "/api/listings", { token: b.token });
  check(!feedAfter.listings.some((l) => l.id === listing.id), "sold item leaves the feed");
  const keepSold = await api("DELETE", `/api/listings/${listing.id}`, { token: a.token, expect: [409] });
  check(keepSold.error.code === "sold", "sold listing stays as the record behind ratings");

  console.log("9. Wanted posts");
  const { wanted } = await api("POST", "/api/wanted", { token: b.token, body: { title: "Engineering Graphics drafter", category: "notes_stationery", budget: 400 } });
  const board = await api("GET", "/api/wanted", { token: a.token });
  check(board.wanted.some((w) => w.id === wanted.id), "A sees B's wanted post");
  const respond = await api("POST", `/api/wanted/${wanted.id}/respond`, { token: a.token, body: { note: "I have a mini drafter, barely used." } });
  check(respond.firstResponse && respond.author.id === b.user.id, "A responded to the wanted post");
  const mine = await api("GET", "/api/wanted?mine=1", { token: b.token });
  check(mine.wanted[0].responseCount === 1, "B sees one response");

  console.log("10. Safety");
  const { listing: l2 } = await api("POST", "/api/listings", {
    token: a.token,
    body: { title: "Desk lamp", category: "hostel_room", condition: "good", price: 250, photos: [upload.url] },
  });
  const { listing: l3 } = await api("POST", "/api/listings", {
    token: a.token,
    body: { title: "Study lamp", category: "hostel_room", condition: "good", price: 300, photos: [upload.url] },
  });
  const o3 = await api("POST", "/api/offers", { token: b.token, body: { listingId: l3.id, amount: 200 } });
  const c3 = await api("POST", `/api/offers/${o3.offer.id}/counter`, { token: a.token, body: { amount: 260 } });
  await api("POST", `/api/listings/${l2.id}/hide`, { token: b.token });
  const hiddenFeed = await api("GET", "/api/listings", { token: b.token });
  check(!hiddenFeed.listings.some((l) => l.id === l2.id), "hidden listing disappears for B");
  await api("POST", "/api/reports", { token: b.token, body: { listingId: l2.id, userId: a.user.id, reason: "other", details: "E2E test report" } });
  await api("POST", "/api/blocks", { token: b.token, body: { userId: a.user.id } });
  await api("GET", `/api/listings/${l2.id}`, { token: b.token, expect: [404] });
  check(true, "blocked seller's listing is hidden from B");
  const blockedChat = await api("POST", "/api/chats/start", { token: a.token, body: { listingId: l2.id }, expect: [400, 403] });
  check(Boolean(blockedChat.error), "blocked users can't start chats");
  await api("POST", `/api/offers/${c3.offer.id}/accept`, { token: b.token, expect: [403] });
  const mineA = await api("GET", "/api/me/listings", { token: a.token });
  check(mineA.listings.find((l) => l.id === l3.id)?.status === "active", "blocked students can't accept offers or reserve items");
  await api("DELETE", `/api/blocks/${a.user.id}`, { token: b.token });
  await api("DELETE", `/api/listings/${l2.id}`, { token: a.token });
  check(true, "unblock + delete work");

  console.log("11. Token refresh");
  const refreshed = await api("POST", "/api/me/nexconn-token", { token: b.token });
  check(refreshed.userId === b.nexconn.userId && refreshed.token.length > 20, "Nexconn token re-issued");

  console.log("12. Account deletion");
  const wrongPw = await api("DELETE", "/api/me", { token: b.token, body: { password: "not-the-password" }, expect: [401] });
  check(wrongPw.error.code === "invalid_password", "deletion needs the right password");
  await api("DELETE", "/api/me", { token: b.token, body: { password: "campus-pass-2" } });
  await api("GET", "/api/me", { token: b.token, expect: [401] });
  await api("POST", "/api/auth/login", { body: { email: email("b"), password: "campus-pass-2" }, expect: [401] });
  check(true, "deleted account is signed out everywhere and can't sign in");
  const ghost = await api("GET", `/api/users/${b.user.id}`, { token: a.token });
  const threadsAfter = await api("GET", "/api/chats/threads", { token: a.token });
  check(
    ghost.user.name === "Deleted student" && threadsAfter.threads.some((t) => t.id === chat.threadId),
    "seller keeps the deal history with an anonymised buyer",
  );
  const boardAfter = await api("GET", "/api/wanted", { token: a.token });
  check(!boardAfter.wanted.some((w) => w.id === wanted.id), "deleted student's wanted posts come down");
  const c = await api("POST", "/api/auth/signup", { body: { name: "E2E Web Delete", email: email("c"), password: "campus-pass-3" } });
  await api("POST", "/api/auth/delete-account", { body: { email: email("c"), password: "not-the-password" }, expect: [401] });
  await api("POST", "/api/auth/delete-account", { body: { email: email("c"), password: "campus-pass-3" } });
  await api("POST", "/api/auth/login", { body: { email: email("c"), password: "campus-pass-3" }, expect: [401] });
  check(true, "account deletion from the website works");

  console.log(`\nPASSED ${passed} checks`);
  return { users: [a.user.id, b.user.id, c.user.id], campusId: created.campus.id };
}

main()
  .then(async (ids) => {
    if (!KEEP) {
      const { cleanup } = await import("./cleanup-e2e.mjs");
      await cleanup(ids.users);
    } else {
      console.log("Kept test data:", ids);
    }
  })
  .catch((err) => {
    console.error(`\nE2E FAILED after ${passed} checks:`, err.message);
    process.exit(1);
  });
