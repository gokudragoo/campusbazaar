import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter, SiteHeader } from "@/components/site/chrome";
import { site } from "@/lib/site";

export const metadata: Metadata = {
  title: "Privacy policy",
  description: "What CampusBazaar collects, who processes it, who can see it, and how to delete your account.",
  alternates: { canonical: "/privacy" },
};

export default function PrivacyPage() {
  return (
    <>
      <SiteHeader />
      <main id="main">
        <header className="wrap page-hero">
          <h1>Privacy policy</h1>
          <p className="lede">CampusBazaar collects what it needs to run a campus marketplace and nothing more. No ads, no analytics trackers, no selling your data.</p>
          <p className="page-hero__meta">Last updated {site.updated}</p>
        </header>
        <article className="wrap">
          <div className="prose">
            <h2>Who we are</h2>
            <p>
              CampusBazaar is an independent student marketplace app for Android with a web API, built on the Nexconn chat platform. For
              privacy questions, open an issue on <a href={site.issues}>GitHub</a> without including personal details, and we&apos;ll
              follow up.
            </p>

            <h2>What we collect</h2>
            <h3>Your account</h3>
            <p>
              Your name, email address and password (stored only as a bcrypt hash, never in plain text), the campus you pick, and, if you
              add them, a profile photo, your hostel and your department.
            </p>
            <h3>What you post and do</h3>
            <p>
              Listings (photos, title, description, price, condition, preferred meetup spot), wanted posts and responses, offers, meetups,
              ratings and comments, reports, blocks, and the listings you save or hide.
            </p>
            <h3>Chat</h3>
            <p>
              Messages you send, including photos, offer and meetup cards, and any location pin you choose to share. Chat is delivered and
              stored by Nexconn, our chat provider.
            </p>
            <h3>Technical data</h3>
            <p>
              Your IP address is used for short-lived counters that stop people hammering sign-up and sign-in. On your phone, your session
              is kept in storage encrypted with the Android Keystore, and recent feed pages are cached so the app works offline.
            </p>
            <h3>What we don&apos;t collect</h3>
            <p>
              Your phone number, contacts, background location or advertising ID. The app contains no advertising or analytics SDKs.
            </p>

            <h2>Camera and location</h2>
            <p>
              The camera is used only when you take photos for a listing. Location is read only when you tap to share your position in a
              chat, and is sent to that one student as a message. CampusBazaar never tracks your location in the background.
            </p>

            <h2>How we use it</h2>
            <ul>
              <li>To run the marketplace: show your listings to students on your campus, deliver chats, and keep offers and meetups in step.</li>
              <li>To send in-app notices about your deals, such as reservations, sales, ratings and replies to your wanted posts.</li>
              <li>To keep people safe: apply blocks, review reports and stop abuse.</li>
              <li>To draft listings: photos you add on the Sell screen are sent to Google Gemini to suggest a title, category, condition, description and price range.</li>
            </ul>
            <p>We don&apos;t sell your data or share it with advertisers.</p>

            <h2>Who processes it</h2>
            <ul>
              <li>
                <strong>Vercel</strong> hosts the CampusBazaar API (Singapore region) and stores listing and profile photos in Vercel Blob.
              </li>
              <li>
                <strong>Neon</strong> hosts the Postgres database with accounts, listings and deals.
              </li>
              <li>
                <strong>Nexconn</strong> carries and stores chat messages and campus channels in its Singapore data center.
              </li>
              <li>
                <strong>Google</strong> processes listing photos through the Gemini API, under Google&apos;s API terms, when you use the listing
                helper.
              </li>
            </ul>

            <h2>Who can see what</h2>
            <ul>
              <li>Students on your campus see your name, photo, hostel, department, ratings, items sold, and your listings and wanted posts.</li>
              <li>Your email address is never shown to other students.</li>
              <li>Chats are visible only to the two students in them. Campus Square messages are visible to everyone in that campus&apos;s Square.</li>
              <li>Listing photos are stored at unguessable public links, so anyone who has the link can open the image.</li>
            </ul>

            <h2 id="deletion">Deleting your account</h2>
            <p>
              In the app, open <strong>Profile</strong>, tap the menu and choose <strong>Delete account</strong>, then confirm with your
              password. Without the app, use the <Link href="/delete-account">account deletion page</Link> on this website.
            </p>
            <p>
              Deleting your account erases your name, email, photo, hostel and department, takes down your listings and wanted posts,
              clears your saved items, hidden items and blocks, and signs you out on every device. Deals and ratings you were part of stay
              in the other student&apos;s history, shown as from &ldquo;Deleted student&rdquo;, and so do messages you already sent, the
              same as any messaging app. Reports involving you are kept without your profile details so abuse can still be dealt with.
            </p>

            <h2>Keeping it secure</h2>
            <p>
              Everything travels over HTTPS. Passwords are hashed with bcrypt. The Nexconn App Secret and other keys live only on the server;
              the app receives a per-user chat token and nothing else.
            </p>

            <h2>Age</h2>
            <p>CampusBazaar is meant for college students aged 18 or over.</p>

            <h2>Changes</h2>
            <p>If this policy changes in a way that matters, we&apos;ll update the date at the top and tell you in the app.</p>
          </div>
        </article>
      </main>
      <SiteFooter />
    </>
  );
}
