import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { notify } from "@/lib/deals";
import { getListingOr404, usersById } from "@/lib/dto";
import { conflict, forbidden, json, parseBody, route } from "@/lib/http";
import { randomId } from "@/lib/ids";
import { ratingCreateSchema } from "@/lib/validation";

/** After a sale, the seller and the buyer each rate the other once. */
export const POST = route(async (req) => {
  const user = await requireUser(req);
  const body = await parseBody(req, ratingCreateSchema);
  const listing = await getListingOr404(body.listingId);
  if (listing.status !== "sold" || !listing.soldToId) throw conflict("You can rate once the seller marks the item sold to you.");

  let rateeId: string;
  let rateeRole: "buyer" | "seller";
  if (user.id === listing.sellerId) {
    rateeId = listing.soldToId;
    rateeRole = "buyer";
  } else if (user.id === listing.soldToId) {
    rateeId = listing.sellerId;
    rateeRole = "seller";
  } else {
    throw forbidden("Only the buyer and seller of this deal can rate it.");
  }

  const [rating] = await db()
    .insert(schema.ratings)
    .values({ id: randomId(14), listingId: listing.id, raterId: user.id, rateeId, rateeRole, stars: body.stars, comment: body.comment })
    .onConflictDoNothing({ target: [schema.ratings.listingId, schema.ratings.raterId] })
    .returning();
  if (!rating) throw conflict("You already rated this deal.", "already_rated");

  const ratee = (await usersById([rateeId])).get(rateeId);
  if (ratee) {
    await notify([ratee], {
      kind: "rate_deal",
      title: `${user.name} rated you ${body.stars} out of 5`,
      body: body.comment ? `"${body.comment.slice(0, 120)}" for ${listing.title}` : `For ${listing.title}`,
      listingId: listing.id,
      peerUserId: user.nexconnUserId,
    });
  }
  return json(
    { rating: { id: rating.id, listingId: rating.listingId, rateeId, rateeRole, stars: rating.stars, comment: rating.comment, createdAt: rating.createdAt.toISOString() } },
    { status: 201 },
  );
});
