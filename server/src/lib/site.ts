import { env } from "./env";

/** Facts about the public website and the Android release, shared by every page. */
export const site = {
  name: "CampusBazaar",
  tagline: "Buy and sell with students on your campus",
  description:
    "The marketplace inside your college. Sell used books, calculators, cycles, coolers and notes to students on your campus, haggle with offer cards and meet at a safe spot. Prices in ₹.",
  github: "https://github.com/gokudragoo/campusbazaar",
  issues: "https://github.com/gokudragoo/campusbazaar/issues",
  updated: "21 November 2026",
};

/** Stable link for buttons and QR codes; redirects to the current signed APK. */
export const APK_PATH = "/download/apk";

export function apkRelease(): { url: string | null; version: string | null } {
  const url = env.apkUrl();
  const version = url ? (/v(\d+\.\d+\.\d+)/.exec(url)?.[1] ?? null) : null;
  return { url, version };
}
