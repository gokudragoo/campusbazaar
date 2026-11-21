import { and, eq } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { getListingOr404 } from "@/lib/dto";
import { json, route, type RouteParams } from "@/lib/http";
import { assertCanView } from "@/lib/listing-access";

type P = RouteParams<{ id: string }>;

export const POST = route(async (req, { params }: P) => {
  const user = await requireUser(req);
  const listing = await getListingOr404((await params).id);
  await assertCanView(listing, user);
  await db().insert(schema.savedListings).values({ userId: user.id, listingId: listing.id }).onConflictDoNothing();
  return json({ saved: true });
});

export const DELETE = route(async (req, { params }: P) => {
  const user = await requireUser(req);
  const { id } = await params;
  await db()
    .delete(schema.savedListings)
    .where(and(eq(schema.savedListings.userId, user.id), eq(schema.savedListings.listingId, id)));
  return json({ saved: false });
});
