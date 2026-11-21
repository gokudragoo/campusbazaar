import { describe, expect, it } from "vitest";
import { campusKey, normalizeCampusPart, SUBCHANNELS, tidyCampusName } from "@/lib/campus";
import { nexconnId, randomId } from "@/lib/ids";
import { rupees } from "@/lib/deals";
import { listingCreateSchema, listingQuerySchema, meetupCreateSchema, reportCreateSchema, signupSchema } from "@/lib/validation";

describe("campus dedupe", () => {
  it("treats punctuation, case and spacing variants as the same campus", () => {
    expect(campusKey("I.I.T. Delhi", "New  Delhi")).toBe(campusKey("iit delhi", "new delhi"));
    expect(campusKey("St. Xavier's College", "Mumbai")).toBe(campusKey("St Xaviers College", "MUMBAI"));
  });

  it("keeps different campuses apart", () => {
    expect(campusKey("IIT Delhi", "New Delhi")).not.toBe(campusKey("IIT Bombay", "Mumbai"));
    expect(campusKey("NIT", "Trichy")).not.toBe(campusKey("NIT", "Warangal"));
  });

  it("normalises accents and ampersands", () => {
    expect(normalizeCampusPart("Café & Arts")).toBe("cafe and arts");
  });

  it("title-cases names without breaking acronyms", () => {
    expect(tidyCampusName("  iit   delhi ")).toBe("Iit Delhi");
    expect(tidyCampusName("BITS pilani")).toBe("BITS Pilani");
  });

  it("uses subchannel ids Nexconn accepts (letters and digits, max 20)", () => {
    for (const s of SUBCHANNELS) expect(s.id).toMatch(/^[a-zA-Z0-9]{1,20}$/);
  });
});

describe("ids", () => {
  it("generates random alphanumeric ids of the requested length", () => {
    const a = randomId(16);
    const b = randomId(16);
    expect(a).toMatch(/^[a-zA-Z0-9]{16}$/);
    expect(a).not.toBe(b);
  });

  it("prefixes Nexconn ids and keeps them alphanumeric and under 64 bytes", () => {
    const id = nexconnId("u");
    expect(id.startsWith("cbz")).toBe(true);
    expect(id).toMatch(/^[a-zA-Z0-9]+$/);
    expect(id.length).toBeLessThanOrEqual(64);
  });
});

describe("validation", () => {
  it("normalises signup input", () => {
    const parsed = signupSchema.parse({ name: "  Asha   Rao ", email: " Asha@College.EDU ", password: "longenough" });
    expect(parsed.name).toBe("Asha Rao");
    expect(parsed.email).toBe("asha@college.edu");
  });

  it("rejects short passwords", () => {
    expect(() => signupSchema.parse({ name: "Asha", email: "a@b.co", password: "short" })).toThrow();
  });

  it("requires 1-5 photos and whole-rupee prices", () => {
    const base = { title: "Casio fx-991EX", category: "electronics", condition: "good", price: 700, photos: ["https://x.public.blob.vercel-storage.com/a.jpg"] };
    expect(listingCreateSchema.parse(base).negotiable).toBe(true);
    expect(() => listingCreateSchema.parse({ ...base, photos: [] })).toThrow();
    expect(() => listingCreateSchema.parse({ ...base, price: 99.5 })).toThrow();
    expect(() => listingCreateSchema.parse({ ...base, photos: Array(6).fill(base.photos[0]) })).toThrow();
  });

  it("parses feed filters from the query string", () => {
    const q = listingQuerySchema.parse({ condition: "good,like_new", minPrice: "100", sort: "price_asc" });
    expect(q.condition).toEqual(["good", "like_new"]);
    expect(q.minPrice).toBe(100);
    expect(q.limit).toBe(20);
  });

  it("needs a timezone-aware meetup time", () => {
    expect(() => meetupCreateSchema.parse({ threadId: "t", spot: "Library steps", scheduledAt: "2026-11-20T17:00:00+05:30" })).not.toThrow();
    expect(() => meetupCreateSchema.parse({ threadId: "t", spot: "Library steps", scheduledAt: "tomorrow" })).toThrow();
  });

  it("requires a report target", () => {
    expect(() => reportCreateSchema.parse({ reason: "scam" })).toThrow();
    expect(reportCreateSchema.parse({ reason: "scam", userId: "u1" }).details).toBe("");
  });
});

describe("money", () => {
  it("formats rupees with Indian digit grouping", () => {
    expect(rupees(1250)).toBe("₹1,250");
    expect(rupees(125000)).toBe("₹1,25,000");
  });
});
