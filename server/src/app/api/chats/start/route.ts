import { z } from "zod";
import { requireCampusUser } from "@/lib/auth";
import { ensureThread } from "@/lib/deals";
import { getListingOr404, listingDto, publicUser, usersById } from "@/lib/dto";
import { json, parseBody, route } from "@/lib/http";
import { rateLimit } from "@/lib/rate-limit";

const bodySchema = z.object({ listingId: z.string().min(1).max(40) });

/**
 * "Chat with seller": records the negotiation thread and returns what the app needs to open the
 * Nexconn direct channel with the seller and post the listing context card.
 */
export const POST = route(async (req) => {
  const user = await requireCampusUser(req);
  await rateLimit(`chat-start:${user.id}`, 60, 3600);
  const { listingId } = await parseBody(req, bodySchema);
  const listing = await getListingOr404(listingId);
  const { thread, created } = await ensureThread(listing, user);
  const seller = (await usersById([listing.sellerId])).get(listing.sellerId)!;
  return json({
    threadId: thread.id,
    created,
    listing: listingDto(listing),
    peer: publicUser(seller),
  });
});
