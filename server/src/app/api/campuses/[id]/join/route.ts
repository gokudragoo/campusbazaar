import { eq } from "drizzle-orm";
import { requireUser } from "@/lib/auth";
import { moveUserToCampus } from "@/lib/campus-membership";
import { db, schema } from "@/lib/db";
import { campusDto } from "@/lib/dto";
import { json, notFound, route, type RouteParams } from "@/lib/http";
import { rateLimit } from "@/lib/rate-limit";

export const POST = route(async (req, { params }: RouteParams<{ id: string }>) => {
  const user = await requireUser(req);
  const { id } = await params;
  await rateLimit(`campus-join:${user.id}`, 10, 86400, "You've switched campus several times today. Try again tomorrow.");
  const [campus] = await db().select().from(schema.campuses).where(eq(schema.campuses.id, id)).limit(1);
  if (!campus) throw notFound("That campus doesn't exist.");
  const joined = await moveUserToCampus(user, campus);
  return json({ campus: campusDto(joined) });
});
