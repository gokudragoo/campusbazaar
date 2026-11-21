import { randomBytes } from "node:crypto";
import { env } from "./env";

const ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

/** Cryptographically random, URL-safe, alphanumeric id (never sequential). */
export function randomId(length = 16): string {
  const bytes = randomBytes(length * 2);
  let out = "";
  for (let i = 0; i < bytes.length && out.length < length; i++) {
    const b = bytes[i]!;
    // Rejection sampling keeps the distribution uniform.
    if (b < 256 - (256 % ALPHABET.length)) out += ALPHABET[b % ALPHABET.length];
  }
  return out.length === length ? out : randomId(length);
}

/** Nexconn user / channel ids must be letters and digits only and carry the app prefix. */
export function nexconnId(kind: "u" | "camp" | "bot", length = 18): string {
  return `${env.nexconnPrefix()}${kind}${randomId(length)}`;
}

export function botUserId(): string {
  return `${env.nexconnPrefix()}bazaarbot`;
}
