import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter, SiteHeader } from "@/components/site/chrome";
import { site } from "@/lib/site";

export const metadata: Metadata = {
  title: "Terms of use",
  description: "The rules for using CampusBazaar: who can use it, what you can't sell, and what CampusBazaar is and isn't responsible for.",
  alternates: { canonical: "/terms" },
};

export default function TermsPage() {
  return (
    <>
      <SiteHeader />
      <main id="main">
        <header className="wrap page-hero">
          <h1>Terms of use</h1>
          <p className="lede">Plain-language rules for a marketplace between students. By creating an account you agree to them.</p>
          <p className="page-hero__meta">Last updated {site.updated}</p>
        </header>
        <article className="wrap">
          <div className="prose">
            <h2>Who can use CampusBazaar</h2>
            <p>
              College students aged 18 or over. One account per person, under your real name, on the campus you actually attend. Keep your
              password to yourself; you&apos;re responsible for what happens on your account.
            </p>

            <h2>What CampusBazaar is</h2>
            <p>
              CampusBazaar is a noticeboard and chat for students to buy and sell from each other. Every deal is between the buyer and the
              seller. CampusBazaar doesn&apos;t own, inspect or guarantee anything listed, doesn&apos;t take part in deals, and never
              handles payments.
            </p>

            <h2 id="not-allowed">What you can&apos;t sell or post</h2>
            <ul>
              <li>Anything illegal to sell or own in India.</li>
              <li>Alcohol, tobacco, vapes, drugs, and prescription medicines.</li>
              <li>Weapons, fireworks and other dangerous goods.</li>
              <li>Stolen, counterfeit or pirated goods, including copied software and photocopied books sold as originals.</li>
              <li>
                Exam papers before the exam, answer keys, and work meant to be submitted as someone else&apos;s, including paid assignment
                or project writing.
              </li>
              <li>ID cards, mess cards, library cards, hostel passes or anything else your college issued to you or someone else.</li>
              <li>Animals, adult content, and other people&apos;s personal information.</li>
              <li>Anything your college&apos;s rules don&apos;t allow on campus.</li>
            </ul>

            <h2>How to behave</h2>
            <ul>
              <li>Describe items honestly, with real photos, and keep prices in rupees.</li>
              <li>No harassment, hate, threats, spam, fake listings, scams or impersonation.</li>
              <li>Don&apos;t use CampusBazaar to collect people&apos;s contact details or move them to another app to pay in advance.</li>
              <li>
                Follow the <Link href="/safety">safe meetup guide</Link> when you meet.
              </li>
            </ul>

            <h2>Your content</h2>
            <p>
              You own what you post. You let CampusBazaar store it and show it to students on your campus so the marketplace works. Listing
              drafts from the AI helper are suggestions; you&apos;re responsible for what you publish.
            </p>

            <h2>When we step in</h2>
            <p>
              We can remove listings or posts, and suspend or close accounts, that break these terms or put other students at risk. Use
              Report in the app to flag anything that looks wrong.
            </p>

            <h2>No guarantees</h2>
            <p>
              CampusBazaar is provided as it is. It may change, have downtime or stop. To the extent the law allows, CampusBazaar isn&apos;t
              liable for items, payments, meetups or anything else that happens between students, or for losses from using the app.
            </p>

            <h2>Leaving</h2>
            <p>
              You can delete your account at any time from Profile in the app or on the <Link href="/delete-account">account deletion page</Link>.
              The <Link href="/privacy#deletion">privacy policy</Link> explains what that removes.
            </p>

            <h2>Changes and law</h2>
            <p>
              If these terms change in a way that matters, we&apos;ll update the date above and tell you in the app. These terms are governed by
              the laws of India. Questions go to our <a href={site.issues}>GitHub issues</a>.
            </p>
          </div>
        </article>
      </main>
      <SiteFooter />
    </>
  );
}
