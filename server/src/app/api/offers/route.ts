import { z } from "zod";
import { requireCampusUser } from "@/lib/auth";
import { createOffer } from "@/lib/deals";
import { getListingOr404, offerDto } from "@/lib/dto";
import { json, parseBody, route } from "@/lib/http";
import { rateLimit } from "@/lib/rate-limit";
import { offerCreateSchema } from "@/lib/validation";

const bodySchema = offerCreateSchema.extend({ threadId: z.string().max(40).optional() });

/**
 * Creates an offer. The app then sends the offer card (custom Nexconn message with initial
 * metadata) and attaches the resulting message id via POST /api/offers/:id/message.
 */
export const POST = route(async (req) => {
  const user = await requireCampusUser(req);
  await rateLimit(`offer:${user.id}`, 60, 3600, "You've sent a lot of offers this hour. Slow down a little.");
  const body = await parseBody(req, bodySchema);
  const listing = await getListingOr404(body.listingId);
  const { offer, superseded, thread } = await createOffer(listing, user, body.amount, body.threadId);
  return json({ offer: offerDto(offer), superseded: superseded.map(offerDto), threadId: thread.id }, { status: 201 });
});
