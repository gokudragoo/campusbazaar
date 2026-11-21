import { and, eq } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { acceptOffer, counterOffer, declineOffer, getOfferForParticipant, notify, rupees } from "@/lib/deals";
import { listingDto, offerDto, usersById } from "@/lib/dto";
import { forbidden, json, notFound, parseBody, route, type RouteParams } from "@/lib/http";
import { attachMessageSchema, counterSchema } from "@/lib/validation";

/** POST /api/offers/:id/(accept|decline|counter|message) */
export const POST = route(async (req, { params }: RouteParams<{ id: string; action: string }>) => {
  const user = await requireUser(req);
  const { id, action } = await params;
  const offer = await getOfferForParticipant(id, user);

  switch (action) {
    case "message": {
      const { messageId } = await parseBody(req, attachMessageSchema);
      if (offer.fromUserId !== user.id) throw forbidden("Only the sender can attach the chat message.");
      const [updated] = await db()
        .update(schema.offers)
        .set({ messageId })
        .where(and(eq(schema.offers.id, offer.id), eq(schema.offers.fromUserId, user.id)))
        .returning();
      return json({ offer: offerDto(updated!) });
    }
    case "accept": {
      const { offer: accepted, listing } = await acceptOffer(offer, user);
      const users = await usersById([accepted.fromUserId]);
      const maker = users.get(accepted.fromUserId);
      if (maker) {
        await notify([maker], {
          kind: "listing_reserved",
          title: `${user.name} accepted your offer`,
          body: `${listing.title} is reserved at ${rupees(accepted.amount)}. Agree on a meetup spot in chat.`,
          listingId: listing.id,
          peerUserId: user.nexconnUserId,
        });
      }
      return json({ offer: offerDto(accepted), listing: listingDto(listing) });
    }
    case "decline": {
      const declined = await declineOffer(offer, user);
      return json({ offer: offerDto(declined) });
    }
    case "counter": {
      const { amount } = await parseBody(req, counterSchema);
      const { countered, offer: counter } = await counterOffer(offer, user, amount);
      return json({ countered: offerDto(countered), offer: offerDto(counter) }, { status: 201 });
    }
    default:
      throw notFound("Unknown offer action.");
  }
});

