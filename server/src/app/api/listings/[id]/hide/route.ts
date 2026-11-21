import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { getListingOr404 } from "@/lib/dto";
import { badRequest, json, route, type RouteParams } from "@/lib/http";

/** Hides a listing from the viewer's feed (safety / not interested). */
export const POST = route(async (req, { params }: RouteParams<{ id: string }>) => {
  const user = await requireUser(req);
  const listing = await getListingOr404((await params).id);
  if (listing.sellerId === user.id) throw badRequest("You can't hide your own listing. Delete it instead.");
  await db().insert(schema.hiddenListings).values({ userId: user.id, listingId: listing.id }).onConflictDoNothing();
  return json({ hidden: true });
});
