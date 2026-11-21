import { badRequest } from "./http";

export type UploadKind = "listings" | "avatars";

/** True when the URL is a photo the user uploaded through us: our Blob store, in their own folder. */
export function isOwnUpload(url: string, userId: string, kind: UploadKind = "listings"): boolean {
  try {
    const u = new URL(url);
    return u.protocol === "https:" && u.hostname.endsWith(".public.blob.vercel-storage.com") && u.pathname.startsWith(`/cbz/${kind}/${userId}/`);
  } catch {
    return false;
  }
}

/** Rejects photo URLs that weren't uploaded by this user (no third-party hosts, no file: or content: URIs). */
export function assertOwnUploads(urls: string[], userId: string, kind: UploadKind, message = "Use photos uploaded from the app."): void {
  if (!urls.every((u) => isOwnUpload(u, userId, kind))) throw badRequest(message, "invalid_photo");
}
