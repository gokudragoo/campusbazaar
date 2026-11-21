import { put } from "@vercel/blob";
import { requireUser } from "@/lib/auth";
import { env } from "@/lib/env";
import { badRequest, json, route } from "@/lib/http";
import { randomId } from "@/lib/ids";
import { rateLimit } from "@/lib/rate-limit";

const MAX_BYTES = 4 * 1024 * 1024;
const TYPES: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };

/** Photo upload (listing photos and avatars). The app compresses before sending; we store in Blob. */
export const POST = route(async (req) => {
  const user = await requireUser(req);
  await rateLimit(`upload:${user.id}`, 80, 3600, "You've uploaded a lot of photos in the last hour. Try again later.");
  let form: FormData;
  try {
    form = await req.formData();
  } catch {
    throw badRequest("Send the photo as multipart form data in a field named file.");
  }
  const file = form.get("file");
  const kind = form.get("kind") === "avatar" ? "avatars" : "listings";
  if (!(file instanceof File)) throw badRequest("Attach a photo in the field named file.");
  const ext = TYPES[file.type];
  if (!ext) throw badRequest("Use a JPEG, PNG or WebP photo.");
  if (file.size === 0) throw badRequest("That photo is empty.");
  if (file.size > MAX_BYTES) throw badRequest("That photo is larger than 4 MB. Pick a smaller one.");

  const bytes = new Uint8Array(await file.arrayBuffer());
  if (!looksLikeImage(bytes, file.type)) throw badRequest("That file isn't a valid photo.");

  const blob = await put(`cbz/${kind}/${user.id}/${randomId(20)}.${ext}`, Buffer.from(bytes), {
    access: "public",
    contentType: file.type,
    token: env.blobToken(),
    addRandomSuffix: false,
    cacheControlMaxAge: 60 * 60 * 24 * 365,
  });
  return json({ url: blob.url }, { status: 201 });
});

/** Magic-number check so arbitrary files can't be stored under an image content type. */
function looksLikeImage(b: Uint8Array, type: string): boolean {
  if (type === "image/jpeg") return b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff;
  if (type === "image/png") return b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47;
  if (type === "image/webp") return b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 && b[8] === 0x57 && b[9] === 0x45;
  return false;
}
