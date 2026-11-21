import { z } from "zod";
import { CATEGORIES, CONDITIONS } from "./db/schema";

const cleanText = (max: number) =>
  z
    .string()
    .transform((s) => s.replace(/\s+/g, " ").trim())
    .pipe(z.string().max(max));

const multiline = (max: number) =>
  z
    .string()
    .transform((s) => s.replace(/\r\n/g, "\n").replace(/[ \t]+/g, " ").replace(/\n{3,}/g, "\n\n").trim())
    .pipe(z.string().max(max));

export const emailSchema = z
  .string()
  .trim()
  .toLowerCase()
  .pipe(z.email({ message: "Enter a valid email address." }).max(254));

export const signupSchema = z.object({
  name: cleanText(60).pipe(z.string().min(2, "Enter your name (at least 2 characters).")),
  email: emailSchema,
  password: z.string().min(8, "Use at least 8 characters for your password.").max(128),
});

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Enter your password.").max(128),
});

export const deleteAccountSchema = z.object({
  password: z.string().min(1, "Enter your password to confirm.").max(128),
});

export const profileUpdateSchema = z.object({
  name: cleanText(60).pipe(z.string().min(2)).optional(),
  hostel: cleanText(60).nullable().optional(),
  department: cleanText(80).nullable().optional(),
  avatarUrl: z.url().max(1024).nullable().optional(),
});

export const campusCreateSchema = z.object({
  name: cleanText(100).pipe(z.string().min(3, "Enter the full campus name.")),
  city: cleanText(60).pipe(z.string().min(2, "Enter the city.")),
});

export const categorySchema = z.enum(CATEGORIES);
export const conditionSchema = z.enum(CONDITIONS);
export const priceSchema = z.number().int("Price must be a whole number of rupees.").min(0).max(500000, "Price looks too high for a campus sale.");

export const photoUrlsSchema = z.array(z.url()).min(1, "Add at least one photo.").max(5, "You can add up to 5 photos.");

export const listingCreateSchema = z.object({
  title: cleanText(80).pipe(z.string().min(3, "Give your item a title (at least 3 characters).")),
  description: multiline(1500).default(""),
  category: categorySchema,
  condition: conditionSchema,
  price: priceSchema,
  negotiable: z.boolean().default(true),
  meetupSpot: cleanText(80).default(""),
  photos: photoUrlsSchema,
  aiAssisted: z.boolean().default(false),
  aiPriceMin: z.number().int().min(0).nullable().optional(),
  aiPriceMax: z.number().int().min(0).nullable().optional(),
});

export const listingUpdateSchema = z
  .object({
    title: cleanText(80).pipe(z.string().min(3)),
    description: multiline(1500),
    category: categorySchema,
    condition: conditionSchema,
    price: priceSchema,
    negotiable: z.boolean(),
    meetupSpot: cleanText(80),
    photos: photoUrlsSchema,
    status: z.enum(["active", "reserved"]),
  })
  .partial();

export const listingQuerySchema = z.object({
  q: z.string().trim().max(80).optional(),
  category: categorySchema.optional(),
  condition: z
    .string()
    .optional()
    .transform((v) => (v ? v.split(",").filter(Boolean) : []))
    .pipe(z.array(conditionSchema)),
  minPrice: z.coerce.number().int().min(0).optional(),
  maxPrice: z.coerce.number().int().min(0).optional(),
  sort: z.enum(["newest", "price_asc", "price_desc"]).default("newest"),
  cursor: z.string().max(200).optional(),
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

export const wantedCreateSchema = z.object({
  title: cleanText(80).pipe(z.string().min(3, "Say what you need (at least 3 characters).")),
  description: multiline(800).default(""),
  category: categorySchema,
  budget: z.number().int().min(0).max(500000).nullable().optional(),
});

export const offerCreateSchema = z.object({
  listingId: z.string().min(1).max(40),
  amount: priceSchema.pipe(z.number().min(1, "Offer at least ₹1.")),
});

export const counterSchema = z.object({ amount: priceSchema.pipe(z.number().min(1, "Offer at least ₹1.")) });
export const attachMessageSchema = z.object({ messageId: z.string().trim().min(4).max(64) });

export const meetupCreateSchema = z.object({
  threadId: z.string().min(1).max(40),
  spot: cleanText(80).pipe(z.string().min(2, "Pick a meetup spot.")),
  scheduledAt: z.iso.datetime({ offset: true }),
  lat: z.number().min(-90).max(90).nullable().optional(),
  lng: z.number().min(-180).max(180).nullable().optional(),
  note: cleanText(200).default(""),
});

export const ratingCreateSchema = z.object({
  listingId: z.string().min(1).max(40),
  stars: z.number().int().min(1).max(5),
  comment: multiline(400).default(""),
});

export const reportCreateSchema = z
  .object({
    userId: z.string().max(40).optional(),
    listingId: z.string().max(40).optional(),
    reason: z.enum(["scam", "fake_listing", "abusive", "prohibited_item", "no_show", "other"]),
    details: multiline(1000).default(""),
  })
  .refine((v) => v.userId || v.listingId, "Report a user or a listing.");

export const wantedRespondSchema = z.object({
  listingId: z.string().max(40).nullable().optional(),
  note: cleanText(300).default(""),
});
