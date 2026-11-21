import { sql } from "drizzle-orm";
import {
  boolean,
  doublePrecision,
  index,
  integer,
  jsonb,
  pgEnum,
  pgTable,
  primaryKey,
  smallint,
  text,
  timestamp,
  uniqueIndex,
} from "drizzle-orm/pg-core";

export const CATEGORIES = [
  "books",
  "electronics",
  "cycles",
  "hostel_room",
  "notes_stationery",
  "sports",
  "other",
] as const;
export const CONDITIONS = ["new", "like_new", "good", "fair", "for_parts"] as const;

export const listingCategory = pgEnum("listing_category", CATEGORIES);
export const itemCondition = pgEnum("item_condition", CONDITIONS);
export const listingStatus = pgEnum("listing_status", ["active", "reserved", "sold", "removed"]);
export const offerStatus = pgEnum("offer_status", ["pending", "accepted", "declined", "countered", "withdrawn"]);
export const meetupStatus = pgEnum("meetup_status", ["proposed", "confirmed", "declined", "cancelled"]);
export const wantedStatus = pgEnum("wanted_status", ["open", "closed"]);
export const reportStatus = pgEnum("report_status", ["open", "reviewed"]);

const createdAt = () => timestamp("created_at", { withTimezone: true }).notNull().defaultNow();

export const campuses = pgTable(
  "campuses",
  {
    id: text("id").primaryKey(),
    name: text("name").notNull(),
    city: text("city").notNull(),
    /** Normalised "name|city" used to dedupe campuses that differ only in case/punctuation. */
    normKey: text("norm_key").notNull(),
    communityChannelId: text("community_channel_id").notNull(),
    createdById: text("created_by_id"),
    memberCount: integer("member_count").notNull().default(0),
    createdAt: createdAt(),
  },
  (t) => [uniqueIndex("campuses_norm_key_idx").on(t.normKey), uniqueIndex("campuses_channel_idx").on(t.communityChannelId)],
);

export const users = pgTable(
  "users",
  {
    id: text("id").primaryKey(),
    nexconnUserId: text("nexconn_user_id").notNull(),
    name: text("name").notNull(),
    email: text("email").notNull(),
    passwordHash: text("password_hash").notNull(),
    campusId: text("campus_id").references(() => campuses.id),
    avatarUrl: text("avatar_url"),
    hostel: text("hostel"),
    department: text("department"),
    createdAt: createdAt(),
  },
  (t) => [uniqueIndex("users_email_idx").on(t.email), uniqueIndex("users_nexconn_idx").on(t.nexconnUserId), index("users_campus_idx").on(t.campusId)],
);

export const listings = pgTable(
  "listings",
  {
    id: text("id").primaryKey(),
    campusId: text("campus_id").notNull().references(() => campuses.id),
    sellerId: text("seller_id").notNull().references(() => users.id),
    title: text("title").notNull(),
    description: text("description").notNull().default(""),
    category: listingCategory("category").notNull(),
    condition: itemCondition("condition").notNull(),
    price: integer("price").notNull(),
    negotiable: boolean("negotiable").notNull().default(true),
    meetupSpot: text("meetup_spot").notNull().default(""),
    photos: jsonb("photos").$type<string[]>().notNull().default(sql`'[]'::jsonb`),
    status: listingStatus("status").notNull().default("active"),
    reservedForId: text("reserved_for_id").references(() => users.id),
    soldToId: text("sold_to_id").references(() => users.id),
    aiAssisted: boolean("ai_assisted").notNull().default(false),
    aiPriceMin: integer("ai_price_min"),
    aiPriceMax: integer("ai_price_max"),
    createdAt: createdAt(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
    soldAt: timestamp("sold_at", { withTimezone: true }),
  },
  (t) => [
    index("listings_feed_idx").on(t.campusId, t.status, t.createdAt),
    index("listings_seller_idx").on(t.sellerId),
  ],
);

export const savedListings = pgTable(
  "saved_listings",
  {
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    listingId: text("listing_id").notNull().references(() => listings.id, { onDelete: "cascade" }),
    createdAt: createdAt(),
  },
  (t) => [primaryKey({ columns: [t.userId, t.listingId] })],
);

export const hiddenListings = pgTable(
  "hidden_listings",
  {
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    listingId: text("listing_id").notNull().references(() => listings.id, { onDelete: "cascade" }),
    createdAt: createdAt(),
  },
  (t) => [primaryKey({ columns: [t.userId, t.listingId] })],
);

export const wantedPosts = pgTable(
  "wanted_posts",
  {
    id: text("id").primaryKey(),
    campusId: text("campus_id").notNull().references(() => campuses.id),
    userId: text("user_id").notNull().references(() => users.id),
    title: text("title").notNull(),
    description: text("description").notNull().default(""),
    category: listingCategory("category").notNull(),
    budget: integer("budget"),
    status: wantedStatus("status").notNull().default("open"),
    responseCount: integer("response_count").notNull().default(0),
    createdAt: createdAt(),
  },
  (t) => [index("wanted_feed_idx").on(t.campusId, t.status, t.createdAt)],
);

export const wantedResponses = pgTable(
  "wanted_responses",
  {
    id: text("id").primaryKey(),
    wantedId: text("wanted_id").notNull().references(() => wantedPosts.id, { onDelete: "cascade" }),
    responderId: text("responder_id").notNull().references(() => users.id),
    listingId: text("listing_id").references(() => listings.id),
    note: text("note").notNull().default(""),
    createdAt: createdAt(),
  },
  (t) => [uniqueIndex("wanted_responses_unique_idx").on(t.wantedId, t.responderId)],
);

/** One negotiation thread per (listing, buyer). Chat itself lives in the buyer–seller Nexconn direct channel. */
export const threads = pgTable(
  "threads",
  {
    id: text("id").primaryKey(),
    listingId: text("listing_id").notNull().references(() => listings.id),
    buyerId: text("buyer_id").notNull().references(() => users.id),
    sellerId: text("seller_id").notNull().references(() => users.id),
    createdAt: createdAt(),
    sellerRepliedAt: timestamp("seller_replied_at", { withTimezone: true }),
    lastActivityAt: timestamp("last_activity_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex("threads_listing_buyer_idx").on(t.listingId, t.buyerId),
    index("threads_buyer_idx").on(t.buyerId),
    index("threads_seller_idx").on(t.sellerId),
  ],
);

export const offers = pgTable(
  "offers",
  {
    id: text("id").primaryKey(),
    listingId: text("listing_id").notNull().references(() => listings.id),
    threadId: text("thread_id").notNull().references(() => threads.id),
    buyerId: text("buyer_id").notNull().references(() => users.id),
    sellerId: text("seller_id").notNull().references(() => users.id),
    fromUserId: text("from_user_id").notNull().references(() => users.id),
    amount: integer("amount").notNull(),
    status: offerStatus("status").notNull().default("pending"),
    parentOfferId: text("parent_offer_id"),
    messageId: text("message_id"),
    createdAt: createdAt(),
    respondedAt: timestamp("responded_at", { withTimezone: true }),
  },
  (t) => [index("offers_thread_idx").on(t.threadId), index("offers_listing_idx").on(t.listingId)],
);

export const meetups = pgTable(
  "meetups",
  {
    id: text("id").primaryKey(),
    listingId: text("listing_id").notNull().references(() => listings.id),
    threadId: text("thread_id").notNull().references(() => threads.id),
    proposedById: text("proposed_by_id").notNull().references(() => users.id),
    buyerId: text("buyer_id").notNull().references(() => users.id),
    sellerId: text("seller_id").notNull().references(() => users.id),
    spot: text("spot").notNull(),
    scheduledAt: timestamp("scheduled_at", { withTimezone: true }).notNull(),
    lat: doublePrecision("lat"),
    lng: doublePrecision("lng"),
    note: text("note").notNull().default(""),
    status: meetupStatus("status").notNull().default("proposed"),
    messageId: text("message_id"),
    createdAt: createdAt(),
    respondedAt: timestamp("responded_at", { withTimezone: true }),
  },
  (t) => [index("meetups_thread_idx").on(t.threadId)],
);

export const ratings = pgTable(
  "ratings",
  {
    id: text("id").primaryKey(),
    listingId: text("listing_id").notNull().references(() => listings.id),
    raterId: text("rater_id").notNull().references(() => users.id),
    rateeId: text("ratee_id").notNull().references(() => users.id),
    /** Role the ratee played in the deal. */
    rateeRole: text("ratee_role").$type<"buyer" | "seller">().notNull(),
    stars: smallint("stars").notNull(),
    comment: text("comment").notNull().default(""),
    createdAt: createdAt(),
  },
  (t) => [uniqueIndex("ratings_listing_rater_idx").on(t.listingId, t.raterId), index("ratings_ratee_idx").on(t.rateeId)],
);

export const blocks = pgTable(
  "blocks",
  {
    blockerId: text("blocker_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    blockedId: text("blocked_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    createdAt: createdAt(),
  },
  (t) => [primaryKey({ columns: [t.blockerId, t.blockedId] })],
);

export const reports = pgTable("reports", {
  id: text("id").primaryKey(),
  reporterId: text("reporter_id").notNull().references(() => users.id),
  targetUserId: text("target_user_id").references(() => users.id),
  listingId: text("listing_id").references(() => listings.id),
  reason: text("reason").notNull(),
  details: text("details").notNull().default(""),
  status: reportStatus("status").notNull().default("open"),
  createdAt: createdAt(),
});

export const rateLimits = pgTable("rate_limits", {
  key: text("key").primaryKey(),
  windowStart: timestamp("window_start", { withTimezone: true }).notNull().defaultNow(),
  count: integer("count").notNull().default(0),
});

export type User = typeof users.$inferSelect;
export type Campus = typeof campuses.$inferSelect;
export type Listing = typeof listings.$inferSelect;
export type Offer = typeof offers.$inferSelect;
export type Meetup = typeof meetups.$inferSelect;
export type Thread = typeof threads.$inferSelect;
export type WantedPost = typeof wantedPosts.$inferSelect;
