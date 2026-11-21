import { env } from "@/lib/env";

export const dynamic = "force-dynamic";

const features = [
  ["Sell in under a minute", "Snap up to 5 photos. Gemini drafts the title, category, condition, description and a fair used price in rupees."],
  ["Negotiate with offer cards", "Chat on Nexconn, send an offer, counter, accept. An accepted offer reserves the item for that buyer."],
  ["Meet safely on campus", "Propose a spot and time with a meetup card, share a map pin, mark it sold and rate each other."],
  ["Campus Square", "Every campus gets a community channel with #general, #books, #electronics, #rides-and-trips and #lost-and-found."],
] as const;

export default function Home() {
  const apk = env.apkUrl();
  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "56px 16px 72px" }}>
      <p style={{ margin: 0, color: "#545c70", fontWeight: 600 }}>CampusBazaar</p>
      <h1 style={{ fontSize: "clamp(32px, 6vw, 48px)", lineHeight: 1.08, margin: "16px 0 12px", letterSpacing: "-0.01em" }}>
        Buy and sell with students on your campus
      </h1>
      <p style={{ fontSize: 18, lineHeight: 1.55, color: "#545c70", maxWidth: 600 }}>
        Old books, calculators, cycles, coolers and notes. Chat, make an offer, and meet at a safe spot on campus. Prices in ₹.
      </p>
      {apk ? (
        <a
          href={apk}
          style={{ display: "inline-block", marginTop: 20, background: "#f5b800", color: "#16213e", padding: "14px 22px", borderRadius: 8, fontWeight: 700, textDecoration: "none" }}
        >
          Download the Android app (APK)
        </a>
      ) : null}
      <p style={{ fontSize: 14, color: "#545c70", marginTop: 10 }}>Android 8.0 or newer. Allow installs from your browser when prompted.</p>
      <ul style={{ listStyle: "none", padding: 0, marginTop: 40, display: "grid", gap: 12 }}>
        {features.map(([title, body]) => (
          <li key={title} style={{ background: "#fff", border: "1px solid #dcdfe6", borderRadius: 8, padding: "16px 18px" }}>
            <strong style={{ display: "block", marginBottom: 4 }}>{title}</strong>
            <span style={{ color: "#545c70", lineHeight: 1.5 }}>{body}</span>
          </li>
        ))}
      </ul>
      <p style={{ fontSize: 13, color: "#8a90a0", marginTop: 40 }}>Real-time chat by Nexconn. This server is the CampusBazaar API; the app is the product.</p>
    </main>
  );
}
