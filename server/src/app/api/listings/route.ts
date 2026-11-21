import { and, asc, desc, eq, gte, ilike, inArray, lte, notExists, notInArray, or, type SQL } from "drizzle-orm";
import { requireCampusUser } from "@/lib/auth";
import { db, schema } from "@/lib/db";
import { blockedUserIds, listingDto, usersById } from "@/lib/dto";
import { json, parseBody, parseQuery, route } from "@/lib/http";
import { randomId } from "@/lib/ids";
import { rateLimit } from "@/lib/rate-limit";
import { listingCreateSchema, listingQuerySchema } from "@/lib/validation";

/** Campus feed with search, category, condition and price filters. Offset cursor. */
export const GET = route(async (req) => {
  const user = await requireCampusUser(req);
  const q = parseQuery(req, listingQuerySchema);
  const blocked = await blockedUserIds(user.id);
  const L = schema.listings;

  const where: SQL[] = [eq(L.campusId, user.campusId), inArray(L.status, ["active", "reserved"])];
  if (blocked.length) where.push(notInArray(L.sellerId, blocked));
  if (q.category) where.push(eq(L.category, q.category));
  if (q.condition.length) where.push(inArray(L.condition, q.condition));
  if (q.minPrice !== undefined) where.push(gte(L.price, q.minPrice));
  if (q.maxPrice !== undefined) where.push(lte(L.price, q.maxPrice));
  if (q.q) {
    const term = `%${q.q.replace(/[%_\\]/g, (c) => `\\${c}`)}%`;
    where.push(or(ilike(L.title, term), ilike(L.description, term))!);
  }
  where.push(
    notExists(
      db()
        .select({ one: schema.hiddenListings.listingId })
        .from(schema.hiddenListings)
        .where(and(eq(schema.hiddenListings.listingId, L.id), eq(schema.hiddenListings.userId, user.id))),
    ),
  );

  const offset = Math.max(0, Number.parseInt(q.cursor ?? "0", 10) || 0);
  const order =
    q.sort === "price_asc"
      ? [asc(L.price), desc(L.createdAt)]
      : q.sort === "price_desc"
        ? [desc(L.price), desc(L.createdAt)]
        : [desc(L.createdAt)];
  const rows = await db()
    .select()
    .from(L)
    .where(and(...where))
    .orderBy(...order, desc(L.id))
    .limit(q.limit + 1)
    .offset(offset);

  const page = rows.slice(0, q.limit);
  const ids = page.map((r) => r.id);
  const [sellers, saved] = await Promise.all([
    usersById(page.map((r) => r.sellerId)),
    ids.length
      ? db()
          .select({ id: schema.savedListings.listingId })
          .from(schema.savedListings)
          .where(and(eq(schema.savedListings.userId, user.id), inArray(schema.savedListings.listingId, ids)))
      : Promise.resolve([]),
  ]);
  const savedSet = new Set(saved.map((s) => s.id));
  return json({
    listings: page.map((l) => {
      const s = sellers.get(l.sellerId);
      return {
        ...listingDto(l, { saved: savedSet.has(l.id) }),
        sellerName: s?.name ?? "Student",
        sellerAvatarUrl: s?.avatarUrl ?? null,
      };
    }),
    nextCursor: rows.length > q.limit ? String(offset + q.limit) : null,
  });
});

export const POST = route(async (req) => {
  const user = await requireCampusUser(req);
  await rateLimit(`listing-create:${user.id}`, 30, 86400, "You've posted a lot of items today. Try again tomorrow.");
  const body = await parseBody(req, listingCreateSchema);
  const [listing] = await db()
    .insert(schema.listings)
    .values({
      id: randomId(14),
      campusId: user.campusId,
      sellerId: user.id,
      title: body.title,
      description: body.description,
      category: body.category,
      condition: body.condition,
      price: body.price,
      negotiable: body.negotiable,
      meetupSpot: body.meetupSpot,
      photos: body.photos,
      aiAssisted: body.aiAssisted,
      aiPriceMin: body.aiPriceMin ?? null,
      aiPriceMax: body.aiPriceMax ?? null,
    })
    .returning();
  return json({ listing: listingDto(listing!) }, { status: 201 });
});
