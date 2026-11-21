import { and, eq, inArray } from "drizzle-orm";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { notify, rupees } from "@/lib/deals";
import { getListingOr404, listingDto, usersById } from "@/lib/dto";
import { badRequest, conflict, json, parseBody, route, type RouteParams } from "@/lib/http";
import { assertOwner } from "@/lib/listing-access";

const bodySchema = z.object({ buyerId: z.string().max(40).nullable().optional() });

/**
 * Seller marks the item sold, optionally to a buyer they negotiated with (enables mutual ratings).
 * The buyer gets a system-channel notice asking them to rate the deal.
 */
export const POST = route(async (req, { params }: RouteParams<{ id: string }>) => {
  const user = await requireUser(req);
  const listing = await getListingOr404((await params).id);
  assertOwner(listing, user);
  if (listing.status === "sold") throw conflict("This item is already marked sold.");
  const body = await parseBody(req, bodySchema);
  // Omitted: sell to the reserved buyer. Empty string or null: sold outside the app (no ratings).
  const buyerId = body.buyerId === undefined ? listing.reservedForId : body.buyerId || null;

  if (buyerId) {
    const [thread] = await db()
      .select({ id: schema.threads.id })
      .from(schema.threads)
      .where(and(eq(schema.threads.listingId, listing.id), eq(schema.threads.buyerId, buyerId)))
      .limit(1);
    if (!thread) throw badRequest("Pick a buyer who chatted with you about this item.");
  }

  const [updated] = await db()
    .update(schema.listings)
    .set({ status: "sold", soldToId: buyerId ?? null, soldAt: new Date(), updatedAt: new Date() })
    .where(and(eq(schema.listings.id, listing.id), inArray(schema.listings.status, ["active", "reserved"])))
    .returning();
  if (!updated) throw conflict("This listing changed in the meantime. Refresh and try again.");

  if (buyerId) {
    const buyer = (await usersById([buyerId])).get(buyerId);
    const [accepted] = await db()
      .select({ amount: schema.offers.amount })
      .from(schema.offers)
      .where(and(eq(schema.offers.listingId, listing.id), eq(schema.offers.buyerId, buyerId), eq(schema.offers.status, "accepted")))
      .limit(1);
    if (buyer) {
      await notify([buyer], {
        kind: "rate_deal",
        title: `Deal done: ${updated.title}`,
        body: `${user.name} marked it sold${accepted ? ` at ${rupees(accepted.amount)}` : ""}. Rate how the deal went.`,
        listingId: updated.id,
        peerUserId: user.nexconnUserId,
      });
    }
  }
  return json({ listing: listingDto(updated) });
});
