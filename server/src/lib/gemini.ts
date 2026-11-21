import { ApiError as GenAiApiError, GoogleGenAI, Type } from "@google/genai";
import { z } from "zod";
import { CATEGORIES, CONDITIONS } from "./db/schema";
import { env } from "./env";
import { ApiError } from "./http";

/**
 * Gemini vision: looks at a seller's photos and drafts a listing (title, category, condition,
 * description, fair used price range in INR). Runs server-side only; the key never reaches clients.
 */

export const suggestionSchema = z.object({
  title: z.string().trim().min(2).max(80),
  category: z.enum(CATEGORIES),
  condition: z.enum(CONDITIONS),
  description: z.string().trim().max(600),
  priceMin: z.number().int().min(0).max(500000),
  priceMax: z.number().int().min(0).max(500000),
  confidence: z.enum(["low", "medium", "high"]),
});

export type ListingSuggestion = z.infer<typeof suggestionSchema> & { model: string };

const responseSchema = {
  type: Type.OBJECT,
  properties: {
    title: { type: Type.STRING, description: "Short, specific listing title, max 60 characters. Include brand/model/edition when visible." },
    category: { type: Type.STRING, enum: [...CATEGORIES] },
    condition: { type: Type.STRING, enum: [...CONDITIONS] },
    description: {
      type: Type.STRING,
      description: "2-4 plain sentences a student seller would write: what it is, visible condition, what is included. No emojis, no hype.",
    },
    priceMin: { type: Type.INTEGER, description: "Low end of a fair second-hand price in Indian rupees for a campus sale." },
    priceMax: { type: Type.INTEGER, description: "High end of a fair second-hand price in Indian rupees for a campus sale." },
    confidence: { type: Type.STRING, enum: ["low", "medium", "high"] },
  },
  required: ["title", "category", "condition", "description", "priceMin", "priceMax", "confidence"],
  propertyOrdering: ["title", "category", "condition", "description", "priceMin", "priceMax", "confidence"],
};

const PROMPT = `You help college students in India sell used items to other students on the same campus.
Look at the photos and draft a listing.
Categories: books (textbooks, novels, competitive exam books), electronics (calculators, phones, laptops, earphones, chargers),
cycles (bicycles and accessories), hostel_room (coolers, kettles, mattresses, buckets, lamps, extension boards, furniture),
notes_stationery (handwritten notes, drafters, lab coats, instruments, stationery), sports (bats, rackets, balls, gym gear), other.
Conditions: new (unused, sealed), like_new (barely used, no marks), good (normal wear), fair (heavy wear but works), for_parts (broken).
Price: estimate what a student would realistically pay for this used item on an Indian campus today, in whole rupees.
Used textbooks usually sell at 30-60% of MRP; scientific calculators like Casio fx-991 sell for roughly 500-900 used.
Keep priceMin <= priceMax and the range tight (max about 40% apart). If you cannot identify the item, say so in the title
plainly (for example "Item in photo") and set confidence to low.`;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

function retriable(err: unknown): boolean {
  if (err instanceof GenAiApiError) return err.status === 429 || err.status >= 500;
  return true; // network errors, timeouts
}

async function fetchImage(url: string): Promise<{ mimeType: string; data: string }> {
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (!res.ok) throw new ApiError(400, "bad_image", "One of the photos could not be loaded. Re-upload it and try again.");
  const mimeType = (res.headers.get("content-type") ?? "image/jpeg").split(";")[0]!.trim();
  if (!mimeType.startsWith("image/")) throw new ApiError(400, "bad_image", "Only photos can be analysed.");
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.byteLength > 6 * 1024 * 1024) throw new ApiError(400, "bad_image", "A photo is too large to analyse.");
  return { mimeType, data: buf.toString("base64") };
}

export async function suggestListing(imageUrls: string[]): Promise<ListingSuggestion> {
  const apiKey = env.geminiApiKey();
  if (!apiKey) throw new ApiError(503, "ai_unavailable", "AI suggestions are not available right now. Fill in the details yourself.");
  const ai = new GoogleGenAI({ apiKey });
  const images = await Promise.all(imageUrls.map(fetchImage));
  const parts = [...images.map((inlineData) => ({ inlineData })), { text: PROMPT }];
  const models = [env.geminiModel(), env.geminiFallbackModel()];

  let lastErr: unknown;
  for (const model of models) {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const response = await ai.models.generateContent({
          model,
          contents: [{ role: "user", parts }],
          config: {
            responseMimeType: "application/json",
            responseSchema,
            temperature: 0.3,
            maxOutputTokens: 700,
            abortSignal: AbortSignal.timeout(20000),
          },
        });
        const text = response.text ?? "";
        const parsed = suggestionSchema.parse(JSON.parse(text));
        const priceMin = Math.min(parsed.priceMin, parsed.priceMax);
        const priceMax = Math.max(parsed.priceMin, parsed.priceMax);
        return { ...parsed, title: parsed.title.slice(0, 60), priceMin, priceMax, model };
      } catch (err) {
        lastErr = err;
        if (err instanceof z.ZodError || err instanceof SyntaxError) break; // malformed output: try the fallback model
        if (!retriable(err)) break;
        await sleep(600 * 2 ** attempt);
      }
    }
  }
  console.error("[gemini] suggestion failed", lastErr instanceof Error ? lastErr.message : lastErr);
  throw new ApiError(503, "ai_busy", "The AI helper is busy right now. Try again in a moment, or fill in the details yourself.");
}
