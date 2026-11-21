import { createHash, randomBytes } from "node:crypto";
import { env } from "./env";
import { botUserId } from "./ids";

/**
 * Minimal, typed client for the Nexconn Platform Chat API (server REST).
 * Every request is signed with SHA1(appSecret + nonce + timestamp). The App Secret never leaves
 * this module and is never logged.
 */

export class NexconnError extends Error {
  constructor(
    public readonly path: string,
    public readonly code: number | string,
    message: string,
  ) {
    super(`Nexconn ${path} failed (${code}): ${message}`);
  }
}

interface NexconnResponse<T> {
  code: number;
  result?: T;
  msg?: string;
  errorMessage?: string;
}

function signedHeaders(): Record<string, string> {
  const nonce = randomBytes(8).toString("hex").slice(0, 16);
  const timestamp = Date.now().toString();
  const signature = createHash("sha1").update(env.nexconnAppSecret() + nonce + timestamp).digest("hex");
  return {
    "App-Key": env.nexconnAppKey(),
    Nonce: nonce,
    Timestamp: timestamp,
    Signature: signature,
    "Content-Type": "application/json",
  };
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Status codes the Platform Chat API documents as transient (HTTP or JSON `code`). */
const TRANSIENT = new Set([429, 500, 502, 503, 504]);
const ATTEMPTS = 4;

/**
 * Calls the Platform Chat API, retrying only transient failures (with the backup host for the
 * later attempts). Business errors throw immediately. Requests that aren't idempotent, such as
 * message sends, are retried only on 429 so a timeout can never deliver a message twice.
 */
async function call<T>(path: string, body: Record<string, unknown>, opts: { idempotent?: boolean } = {}): Promise<T | undefined> {
  const idempotent = opts.idempotent ?? true;
  const hosts = [env.nexconnApiBase(), env.nexconnApiBackup()];
  let lastError: unknown;
  for (let attempt = 0; attempt < ATTEMPTS; attempt++) {
    if (attempt > 0) await sleep(250 * 2 ** (attempt - 1));
    const host = hosts[attempt < 2 ? 0 : 1]!;
    let res: Response;
    try {
      res = await fetch(`${host}${path}`, {
        method: "POST",
        headers: signedHeaders(),
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(8000),
      });
    } catch (err) {
      // Network error or timeout: the request may or may not have been processed.
      lastError = err;
      if (!idempotent) break;
      continue;
    }
    if (TRANSIENT.has(res.status)) {
      lastError = new NexconnError(path, res.status, `HTTP ${res.status}`);
      if (!idempotent && res.status !== 429) break;
      continue;
    }
    const data = (await res.json().catch(() => null)) as NexconnResponse<T> | null;
    if (!data) throw new NexconnError(path, res.status, `unreadable response (HTTP ${res.status})`);
    if (data.code === 0 || data.code === 200) return data.result;
    const error = new NexconnError(path, data.code, data.msg ?? data.errorMessage ?? "request rejected");
    if (!TRANSIENT.has(data.code) || (!idempotent && data.code !== 429)) throw error;
    lastError = error;
  }
  throw lastError instanceof Error ? lastError : new Error(`Nexconn ${path} failed`);
}

const trimName = (name: string) => (name.length > 64 ? name.slice(0, 64) : name);

/** Registers (or re-registers) a user and returns a fresh access token. */
export async function issueAccessToken(userId: string, name: string, avatarUrl?: string | null): Promise<string> {
  const result = await call<{ userId: string; accessToken: string }>("/v4/auth/access-token/issue", {
    userId,
    name: trimName(name),
    ...(avatarUrl ? { avatarUrl } : {}),
  });
  if (!result?.accessToken) throw new NexconnError("/v4/auth/access-token/issue", "no_token", "missing accessToken");
  return result.accessToken;
}

export async function updateUserInfo(userId: string, name: string, avatarUrl?: string | null): Promise<void> {
  await call("/v4/user/update", { userId, name: trimName(name), ...(avatarUrl ? { avatarUrl } : {}) });
}

/** Invalidates every access token issued to the users so far (used when an account is deleted). */
export async function expireAccessTokens(userIds: string[]): Promise<void> {
  await call("/v4/auth/access-token/expire", { userIds, expiresAt: Date.now() });
}

/* ---------------------------------------------------------------- community channels --- */

export async function createCommunityChannel(channelId: string, name: string, ownerUserId: string): Promise<void> {
  await call("/v4/community-channel/create", { channelId, name: trimName(name), userId: ownerUserId });
}

export async function createSubchannel(channelId: string, subchannelId: string): Promise<void> {
  await call("/v4/community-channel/subchannel/create", { channelId, subchannelId, channelVisibility: 0 });
}

export async function listSubchannels(channelId: string): Promise<string[]> {
  const result = await call<{ subchannels?: { subchannelId: string }[] }>("/v4/community-channel/subchannel/list", {
    channelId,
    page: 1,
    pageSize: 100,
  });
  return (result?.subchannels ?? []).map((s) => s.subchannelId);
}

export async function joinCommunityChannel(userId: string, channelId: string): Promise<void> {
  await call("/v4/community-channel/join", { userId, channelId });
}

export async function leaveCommunityChannel(userId: string, channelId: string): Promise<void> {
  await call("/v4/community-channel/leave", { userId, channelId });
}

export async function isCommunityMember(userId: string, channelId: string): Promise<boolean> {
  const result = await call<{ isMember?: boolean }>("/v4/community-channel/member/exist", { userId, channelId });
  return result?.isMember === true;
}

export async function sendCommunityMessage(
  fromUserId: string,
  channelId: string,
  subchannelId: string,
  messageType: string,
  content: Record<string, unknown>,
): Promise<void> {
  await call("/v4/community-channel/message/send", {
    fromUserId,
    toChannelIds: [channelId],
    subchannelId,
    messageType,
    content: JSON.stringify(content),
    shouldPersist: 1,
  }, { idempotent: false });
}

/* ------------------------------------------------------------------------- messaging --- */

export const NOTICE_TYPE = "cbz:notice";

export interface NoticeContent {
  kind: "listing_reserved" | "listing_sold" | "rate_deal" | "wanted_response" | "offer_update" | "meetup_update" | "welcome";
  title: string;
  body: string;
  listingId?: string;
  wantedId?: string;
  peerUserId?: string;
}

let botRegistered = false;

/** The bot account that sends system-channel notices must have been issued a token once. */
async function ensureBot(): Promise<string> {
  const id = botUserId();
  if (!botRegistered) {
    await issueAccessToken(id, "CampusBazaar");
    botRegistered = true;
  }
  return id;
}

/**
 * In-app notification inbox: a Nexconn *system channel* message from the CampusBazaar bot.
 * Recipients see it live (and on next connect) in the system channel; they cannot reply.
 */
export async function sendSystemNotice(toUserIds: string[], notice: NoticeContent): Promise<void> {
  if (toUserIds.length === 0) return;
  const from = await ensureBot();
  await call("/v4/system-channel/message/send", {
    fromUserId: from,
    toUserIds,
    messageType: NOTICE_TYPE,
    content: JSON.stringify(notice),
    pushContent: notice.title,
    shouldPersist: 1,
  }, { idempotent: false });
}

/** Sends a direct-channel message on behalf of `fromUserId` (used for server-authored deal events). */
export async function sendDirectMessage(params: {
  fromUserId: string;
  toUserId: string;
  messageType: string;
  content: Record<string, unknown>;
  pushContent?: string;
  metadata?: Record<string, string>;
}): Promise<string | null> {
  const result = await call<{ messageIds?: { userId: string; messageId: string }[] }>("/v4/direct-channel/message/send", {
    fromUserId: params.fromUserId,
    toUserIds: [params.toUserId],
    messageType: params.messageType,
    content: JSON.stringify(params.content),
    ...(params.pushContent ? { pushContent: params.pushContent } : {}),
    isEchoToSender: 1,
    ...(params.metadata ? { hasMetadata: true, metadata: params.metadata } : {}),
  }, { idempotent: false });
  return result?.messageIds?.[0]?.messageId ?? null;
}

/** Updates metadata of a direct-channel message (e.g. an offer card's state). */
export async function setDirectMessageMetadata(params: {
  messageId: string;
  operatorUserId: string;
  peerUserId: string;
  metadata: Record<string, string>;
}): Promise<void> {
  await call("/v4/channel-type/message/metadata/set", {
    messageId: params.messageId,
    userId: params.operatorUserId,
    channelType: 1,
    channelId: params.peerUserId,
    metadata: params.metadata,
    isEchoToSender: 1,
  });
}

export async function addToBlocklist(userId: string, blockedUserIds: string[]): Promise<void> {
  await call("/v4/user/blocklist/add", { userId, targetUserIds: blockedUserIds });
}

export async function removeFromBlocklist(userId: string, blockedUserIds: string[]): Promise<void> {
  await call("/v4/user/blocklist/remove", { userId, blockedUserIds });
}
