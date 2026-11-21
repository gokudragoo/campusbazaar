import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { suggestListing } from "@/lib/gemini";
import { badRequest, json, parseBody, route } from "@/lib/http";
import { rateLimit } from "@/lib/rate-limit";

export const maxDuration = 60;

const bodySchema = z.object({ imageUrls: z.array(z.url()).min(1, "Add a photo first.").max(5) });

/** Only photos the user uploaded through us (our Blob store, their folder) can be analysed. */
function isOwnUpload(url: string, userId: string): boolean {
  try {
    const u = new URL(url);
    return u.protocol === "https:" && u.hostname.endsWith(".public.blob.vercel-storage.com") && u.pathname.startsWith(`/cbz/listings/${userId}/`);
  } catch {
    return false;
  }
}

export const POST = route(async (req) => {
  const user = await requireUser(req);
  const { imageUrls } = await parseBody(req, bodySchema);
  if (!imageUrls.every((u) => isOwnUpload(u, user.id))) throw badRequest("Only photos you uploaded can be analysed.");
  await rateLimit(`ai:${user.id}`, 20, 3600, "You've used the AI helper a lot this hour. Fill in the details yourself or try later.");
  await rateLimit(`ai-day:${user.id}`, 60, 86400, "You've reached today's AI helper limit. Fill in the details yourself.");
  const suggestion = await suggestListing(imageUrls.slice(0, 3));
  return json({ suggestion });
});
