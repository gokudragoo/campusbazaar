/** Campus helpers shared by routes and tests. */

export const SUBCHANNELS = [
  { id: "general", name: "general", description: "Campus-wide chat, announcements and quick asks" },
  { id: "books", name: "books", description: "Textbooks, novels, exam prep and book swaps" },
  { id: "electronics", name: "electronics", description: "Calculators, laptops, chargers, repairs" },
  { id: "ridestrips", name: "rides-and-trips", description: "Share autos, cabs and trips home" },
  { id: "lostfound", name: "lost-and-found", description: "Lost an ID card or found a bottle? Post here" },
] as const;

const COMBINING_MARKS = /[̀-ͯ]/g;
const APOSTROPHES_AND_DOTS = /[.'’`]/g;

/** Lowercases, strips punctuation and collapses whitespace so "I.I.T. Delhi" and "IIT  delhi" dedupe. */
export function normalizeCampusPart(value: string): string {
  return value
    .normalize("NFKD")
    .replace(COMBINING_MARKS, "")
    .toLowerCase()
    .replace(/&/g, " and ")
    .replace(APOSTROPHES_AND_DOTS, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}

export function campusKey(name: string, city: string): string {
  return `${normalizeCampusPart(name)}|${normalizeCampusPart(city)}`;
}

/** Title-cases user input lightly without destroying acronyms like "IIT" or "NIT". */
export function tidyCampusName(value: string): string {
  return value
    .trim()
    .replace(/\s+/g, " ")
    .split(" ")
    .map((word) => (word === word.toUpperCase() ? word : word.charAt(0).toUpperCase() + word.slice(1)))
    .join(" ");
}
