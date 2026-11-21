import { and, eq } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { assertNotBlocked, touchThread } from "@/lib/deals";
import { meetupDto } from "@/lib/dto";
import { conflict, forbidden, json, notFound, parseBody, route, type RouteParams } from "@/lib/http";
import { attachMessageSchema } from "@/lib/validation";

/** POST /api/meetups/:id/(confirm|decline|cancel|message) */
export const POST = route(async (req, { params }: RouteParams<{ id: string; action: string }>) => {
  const user = await requireUser(req);
  const { id, action } = await params;
  const [meetup] = await db().select().from(schema.meetups).where(eq(schema.meetups.id, id)).limit(1);
  if (!meetup) throw notFound("This meetup no longer exists.");
  if (meetup.buyerId !== user.id && meetup.sellerId !== user.id) throw forbidden();

  const transition = async (status: "confirmed" | "declined" | "cancelled") => {
    const [updated] = await db()
      .update(schema.meetups)
      .set({ status, respondedAt: new Date() })
      .where(and(eq(schema.meetups.id, meetup.id), eq(schema.meetups.status, "proposed")))
      .returning();
    if (!updated) throw conflict(`This meetup was already ${meetup.status}.`, "meetup_closed");
    await touchThread(meetup.threadId);
    return json({ meetup: meetupDto(updated) });
  };

  switch (action) {
    case "message": {
      if (meetup.proposedById !== user.id) throw forbidden("Only the proposer can attach the chat message.");
      const { messageId } = await parseBody(req, attachMessageSchema);
      const [updated] = await db().update(schema.meetups).set({ messageId }).where(eq(schema.meetups.id, meetup.id)).returning();
      return json({ meetup: meetupDto(updated!) });
    }
    case "confirm":
    case "decline":
      if (meetup.proposedById === user.id) throw forbidden("The other student needs to answer this proposal.");
      if (action === "confirm") await assertNotBlocked(meetup.buyerId, meetup.sellerId);
      return transition(action === "confirm" ? "confirmed" : "declined");
    case "cancel":
      if (meetup.proposedById !== user.id) throw forbidden("Only the proposer can cancel this proposal.");
      return transition("cancelled");
    default:
      throw notFound("Unknown meetup action.");
  }
});
