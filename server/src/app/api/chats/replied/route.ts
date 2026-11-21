import { and, eq, isNull } from "drizzle-orm";
import { z } from "zod";
import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { json, parseBody, route } from "@/lib/http";

const bodySchema = z.object({ peerNexconnUserId: z.string().min(1).max(64) });

/**
 * Called by the app the first time a seller sends a message to a buyer. Feeds the seller's
 * response rate (threads with a reply / threads started).
 */
export const POST = route(async (req) => {
  const user = await requireUser(req);
  const { peerNexconnUserId } = await parseBody(req, bodySchema);
  const [peer] = await db()
    .select({ id: schema.users.id })
    .from(schema.users)
    .where(eq(schema.users.nexconnUserId, peerNexconnUserId))
    .limit(1);
  if (!peer) return json({ updated: 0 });
  const rows = await db()
    .update(schema.threads)
    .set({ sellerRepliedAt: new Date(), lastActivityAt: new Date() })
    .where(and(eq(schema.threads.sellerId, user.id), eq(schema.threads.buyerId, peer.id), isNull(schema.threads.sellerRepliedAt)))
    .returning({ id: schema.threads.id });
  return json({ updated: rows.length });
});
