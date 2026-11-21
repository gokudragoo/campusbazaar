#!/usr/bin/env node
/**
 * Drives a second student through the real backend + Nexconn Platform Chat API, so one phone
 * (or emulator) can demo the full two-sided loop. Uses server/.env.local for the Nexconn
 * secret (never shipped to clients). State is kept in a JSON file between steps.
 *
 *   node scripts/second-student.mjs <base> <stateFile> <command> [args...]
 *
 * Commands:
 *   signup <name> <email> <password>      create the student (or log in if it exists)
 *   join <campusQuery>                    join the first campus matching the query
 *   find <query>                          search the campus feed, remember the first hit
 *   chat <text>                           start the chat about the remembered listing, send listing card + text
 *   say <text>                            send a text message to the seller
 *   offer <amount>                        create an offer and send the offer card (with metadata)
 *   accept-latest                         accept the latest pending offer from the seller (+ metadata + deal card)
 *   counter-latest <amount>               counter the latest pending offer from the seller
 *   confirm-meetup                        confirm the latest proposed meetup (+ metadata)
 *   rate <stars> <comment>                rate the seller after the sale
 *   square <subchannelId> <text>          post in a Campus Square subchannel
 *   state                                 print the remembered state
 */
import { createHash, randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const [base, stateFile, command, ...args] = process.argv.slice(2);
if (!base || !stateFile || !command) {
  console.error("usage: node scripts/second-student.mjs <base> <stateFile> <command> [args...]");
  process.exit(2);
}

const env = Object.fromEntries(
  readFileSync(fileURLToPath(new URL("../.env.local", import.meta.url)), "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("=") && !l.startsWith("#"))
    .map((l) => {
      const i = l.indexOf("=");
      let v = l.slice(i + 1);
      if (v.startsWith('"') && v.endsWith('"')) v = v.slice(1, -1);
      return [l.slice(0, i), v];
    }),
);

const state = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, "utf8")) : {};
const save = () => writeFileSync(stateFile, JSON.stringify(state, null, 2));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(method, path, body) {
  for (let i = 0; i < 4; i++) {
    try {
      const res = await fetch(base + path, {
        method,
        headers: { "Content-Type": "application/json", ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}) },
        body: body ? JSON.stringify(body) : undefined,
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw Object.assign(new Error(`${method} ${path} -> ${res.status} ${JSON.stringify(data)}`), { status: res.status });
      return data;
    } catch (e) {
      if (e.status && e.status < 500) throw e;
      if (i === 3) throw e;
      await sleep(800 * 2 ** i);
    }
  }
}

async function nexconn(path, body) {
  const nonce = randomBytes(6).toString("hex");
  const ts = Date.now().toString();
  const sig = createHash("sha1").update(env.NEXCONN_APP_SECRET + nonce + ts).digest("hex");
  for (let i = 0; i < 4; i++) {
    try {
      const res = await fetch(env.NEXCONN_API_BASE + path, {
        method: "POST",
        headers: { "App-Key": env.NEXCONN_APP_KEY, Nonce: nonce, Timestamp: ts, Signature: sig, "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (data.code !== 0 && data.code !== 200) throw new Error(`${path} -> ${JSON.stringify(data)}`);
      return data.result;
    } catch (e) {
      if (i === 3) throw e;
      await sleep(800 * 2 ** i);
    }
  }
}

/** Sends a direct message from this student to the seller through the Platform Chat API. */
async function sendToSeller(messageType, content, pushContent, metadata) {
  const result = await nexconn("/v4/direct-channel/message/send", {
    fromUserId: state.nexconnUserId,
    toUserIds: [state.sellerNexconnId],
    messageType,
    content: JSON.stringify(content),
    pushContent,
    isEchoToSender: 1,
    needReadReceipt: 1,
    ...(metadata ? { hasMetadata: true, metadata } : {}),
  });
  return result?.messageIds?.[0]?.messageId ?? null;
}

async function setMetadata(messageId, metadata) {
  await nexconn("/v4/channel-type/message/metadata/set", {
    messageId,
    userId: state.nexconnUserId,
    channelType: 1,
    channelId: state.sellerNexconnId,
    metadata,
    isEchoToSender: 1,
  });
}

async function peer() {
  return api("GET", `/api/chats/peer/${state.sellerNexconnId}`);
}

const commands = {
  async signup(name, email, password) {
    let r;
    try {
      r = await api("POST", "/api/auth/signup", { name, email, password });
    } catch (e) {
      if (e.status !== 409) throw e;
      r = await api("POST", "/api/auth/login", { email, password });
    }
    Object.assign(state, { token: r.token, userId: r.user.id, nexconnUserId: r.nexconn.userId, name: r.user.name });
    save();
    console.log(`signed in as ${r.user.name} (${r.nexconn.userId}), campus: ${r.campus?.name ?? "none"}`);
  },
  async join(query) {
    const { campuses } = await api("GET", `/api/campuses?q=${encodeURIComponent(query)}`);
    if (!campuses.length) throw new Error(`no campus matches ${query}`);
    const { campus } = await api("POST", `/api/campuses/${campuses[0].id}/join`);
    state.campusId = campus.id;
    save();
    console.log(`joined ${campus.name}, ${campus.city} (${campus.memberCount} students)`);
  },
  async find(query) {
    const { listings } = await api("GET", `/api/listings?q=${encodeURIComponent(query)}`);
    if (!listings.length) throw new Error(`nothing in the feed matches ${query}`);
    const l = listings[0];
    const detail = await api("GET", `/api/listings/${l.id}`);
    Object.assign(state, { listingId: l.id, listingTitle: l.title, price: l.price, sellerNexconnId: detail.listing.seller.nexconnUserId, sellerName: detail.listing.seller.name });
    save();
    console.log(`found "${l.title}" for ₹${l.price} (AI assisted: ${l.aiAssisted}) from ${detail.listing.seller.name}`);
  },
  async chat(...words) {
    const r = await api("POST", "/api/chats/start", { listingId: state.listingId });
    state.threadId = r.threadId;
    save();
    const l = r.listing;
    await sendToSeller("cbz:listing", { listingId: l.id, title: l.title, price: l.price, photo: l.photos[0] ?? "", condition: l.condition, negotiable: l.negotiable }, `${state.name} is asking about ${l.title}`);
    const text = words.join(" ");
    if (text) await sendToSeller("RC:TxtMsg", { content: text }, `${state.name}: ${text}`);
    console.log(`chat opened (thread ${r.threadId}); sent listing card${text ? " and text" : ""}`);
  },
  async say(...words) {
    const text = words.join(" ");
    await sendToSeller("RC:TxtMsg", { content: text }, `${state.name}: ${text}`);
    console.log("sent:", text);
  },
  async offer(amount) {
    const r = await api("POST", "/api/offers", { listingId: state.listingId, amount: Number(amount) });
    const messageId = await sendToSeller(
      "cbz:offer",
      { offerId: r.offer.id, listingId: state.listingId, listingTitle: state.listingTitle, amount: r.offer.amount, askingPrice: state.price, fromUserId: state.nexconnUserId, parentAmount: 0 },
      `${state.name} offered ₹${r.offer.amount}`,
      { state: "pending" },
    );
    if (messageId) await api("POST", `/api/offers/${r.offer.id}/message`, { messageId });
    console.log(`offer ₹${r.offer.amount} sent (offer ${r.offer.id}, message ${messageId})`);
  },
  async "accept-latest"() {
    const p = await peer();
    const thread = p.threads.find((t) => t.listing?.id === state.listingId) ?? p.threads[0];
    const pending = thread.offers.find((o) => o.status === "pending" && o.fromUserId !== state.userId);
    if (!pending) throw new Error("no pending offer from the seller");
    const r = await api("POST", `/api/offers/${pending.id}/accept`);
    if (pending.messageId) await setMetadata(pending.messageId, { state: "accepted", by: state.nexconnUserId, at: String(Date.now()) });
    await sendToSeller("cbz:deal", { kind: "reserved", listingId: state.listingId, listingTitle: state.listingTitle, amount: r.offer.amount }, `${state.name} accepted ₹${r.offer.amount}`);
    console.log(`accepted ₹${r.offer.amount}; listing is now ${r.listing.status}`);
  },
  async "counter-latest"(amount) {
    const p = await peer();
    const thread = p.threads.find((t) => t.listing?.id === state.listingId) ?? p.threads[0];
    const pending = thread.offers.find((o) => o.status === "pending" && o.fromUserId !== state.userId);
    if (!pending) throw new Error("no pending offer from the seller");
    const r = await api("POST", `/api/offers/${pending.id}/counter`, { amount: Number(amount) });
    if (pending.messageId) await setMetadata(pending.messageId, { state: "countered", by: state.nexconnUserId, at: String(Date.now()) });
    const messageId = await sendToSeller(
      "cbz:offer",
      { offerId: r.offer.id, listingId: state.listingId, listingTitle: state.listingTitle, amount: r.offer.amount, askingPrice: state.price, fromUserId: state.nexconnUserId, parentAmount: pending.amount },
      `${state.name} countered with ₹${r.offer.amount}`,
      { state: "pending" },
    );
    if (messageId) await api("POST", `/api/offers/${r.offer.id}/message`, { messageId });
    console.log(`countered with ₹${r.offer.amount}`);
  },
  async "confirm-meetup"() {
    const p = await peer();
    const thread = p.threads.find((t) => t.listing?.id === state.listingId) ?? p.threads[0];
    const m = thread.meetups.find((x) => x.status === "proposed" && x.proposedById !== state.userId);
    if (!m) throw new Error("no proposed meetup from the seller");
    const r = await api("POST", `/api/meetups/${m.id}/confirm`);
    if (m.messageId) await setMetadata(m.messageId, { state: "confirmed", by: state.nexconnUserId, at: String(Date.now()) });
    await sendToSeller("RC:TxtMsg", { content: `See you at ${m.spot}!` }, `${state.name}: See you at ${m.spot}!`);
    console.log(`confirmed meetup at ${r.meetup.spot}, ${r.meetup.scheduledAt}`);
  },
  async rate(stars, ...words) {
    await api("POST", "/api/ratings", { listingId: state.listingId, stars: Number(stars), comment: words.join(" ") });
    console.log(`rated the seller ${stars}/5`);
  },
  async square(subchannelId, ...words) {
    const sq = await api("GET", "/api/campus/square");
    await nexconn("/v4/community-channel/message/send", {
      fromUserId: state.nexconnUserId,
      toChannelIds: [sq.channelId],
      subchannelId,
      messageType: "RC:TxtMsg",
      content: JSON.stringify({ content: words.join(" ") }),
      shouldPersist: 1,
    });
    console.log(`posted in #${subchannelId} of ${sq.campus.name}`);
  },
  async state() {
    console.log(JSON.stringify({ ...state, token: state.token ? "<redacted>" : undefined }, null, 2));
  },
};

const fn = commands[command];
if (!fn) {
  console.error(`unknown command ${command}`);
  process.exit(2);
}
fn(...args).catch((e) => {
  console.error("FAILED:", e.message);
  process.exit(1);
});
