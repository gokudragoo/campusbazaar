import { requireUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { badRequest, json, parseBody, route } from "@/lib/http";
import { randomId } from "@/lib/ids";
import { rateLimit } from "@/lib/rate-limit";
import { reportCreateSchema } from "@/lib/validation";

/** Report a user and/or a listing. Stored for review; the reporter can also block. */
export const POST = route(async (req) => {
  const user = await requireUser(req);
  await rateLimit(`report:${user.id}`, 20, 86400, "You've sent several reports today. We'll review them.");
  const body = await parseBody(req, reportCreateSchema);
  if (body.userId === user.id) throw badRequest("You can't report yourself.");
  const [report] = await db()
    .insert(schema.reports)
    .values({
      id: randomId(14),
      reporterId: user.id,
      targetUserId: body.userId ?? null,
      listingId: body.listingId ?? null,
      reason: body.reason,
      details: body.details,
    })
    .returning({ id: schema.reports.id });
  return json({ reportId: report!.id }, { status: 201 });
});
