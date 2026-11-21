import { NextResponse } from "next/server";
import { env } from "@/lib/env";

/** Stable download link (used by buttons and QR codes) that points at the current signed APK. */
export function GET(req: Request) {
  const apk = env.apkUrl();
  return NextResponse.redirect(apk ?? new URL("/download", req.url), { status: 302, headers: { "Cache-Control": "no-store" } });
}

export const dynamic = "force-dynamic";
