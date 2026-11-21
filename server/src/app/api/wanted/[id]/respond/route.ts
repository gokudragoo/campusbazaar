import { and, eq, sql } from "drizzle-orm";
import { requireCampusUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { ensureThread, notify } from "@/lib/deals";
import { listingDto, publicUser, usersById } from "@/lib/dto";
import { badRequest, conflict, forbidden, json, notFound, parseBody, route, type RouteParams } from "@/lib/http";
import { randomId } from "@/lib/ids";
import { isBlockedEitherWay } from "@/lib/listing-access";
import { rateLimit } from "@/lib/rate-limit";
import { wantedRespondSchema } from "@/lib/validation";

/**
 * A seller responds to a Wanted post, optionally offering one of their active listings. When a
 * listing is attached, a negotiation thread (poster = buyer) is created so offers work in chat.
 */
export const POST = route(async (req, { params }: RouteParams<{ id: string }>) => {
  const user = await requireCampusUser(req);
  await rateLimit(`wanted-respond:${user.id}`, 40, 3600);
  const { id } = await params;
  const body = await parseBody(req, wantedRespondSchema);
  const [post] = await db().select().from(schema.wantedPosts).where(eq(schema.wantedPosts.id, id)).limit(1);
  if (!post || post.campusId !== user.campusId) throw notFound("This request no longer exists.");
  if (post.userId === user.id) throw badRequest("This is your own request.");
  if (post.status !== "open") throw conflict("This request has been closed.");
  if (await isBlockedEitherWay(user.id, post.userId)) throw forbidden("You can't message this student.");

  const author = (await usersById([post.userId])).get(post.userId);
  if (!author) throw notFound("This student's account no longer exists.");

  let listing = null;
  let threadId: string | null = null;
  if (body.listingId) {
    const [row] = await db().select().from(schema.listings).where(eq(schema.listings.id, body.listingId)).limit(1);
    if (!row || row.sellerId !== user.id || row.status !== "active") throw badRequest("Pick one of your active listings.");
    listing = row;
    // The poster becomes the buyer for this listing's negotiation.
    threadId = (await ensureThread(row, author)).thread.id;
  }

  const [inserted] = await db()
    .insert(schema.wantedResponses)
    .values({ id: randomId(14), wantedId: post.id, responderId: user.id, listingId: listing?.id ?? null, note: body.note })
    .onConflictDoNothing({ target: [schema.wantedResponses.wantedId, schema.wantedResponses.responderId] })
    .returning();
  if (inserted) {
    await db()
      .update(schema.wantedPosts)
      .set({ responseCount: sql`${schema.wantedPosts.responseCount} + 1` })
      .where(and(eq(schema.wantedPosts.id, post.id)));
    await notify([author], {
      kind: "wanted_response",
      title: `${user.name} has something for "${post.title}"`,
      body: listing ? `${listing.title} for ₹${listing.price.toLocaleString("en-IN")}. Open the chat to see it.` : body.note || "Open the chat to reply.",
      wantedId: post.id,
      listingId: listing?.id,
      peerUserId: user.nexconnUserId,
    });
  }
  return json({ author: publicUser(author), listing: listing ? listingDto(listing) : null, threadId, firstResponse: Boolean(inserted) });
});
