import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter, SiteHeader } from "@/components/site/chrome";

export const metadata: Metadata = {
  title: "Safe meetup guide",
  description: "How to buy and sell safely with students on your campus: where to meet, what to check, and how to pay without getting scammed.",
  alternates: { canonical: "/safety" },
};

export default function SafetyPage() {
  return (
    <>
      <SiteHeader />
      <main id="main">
        <header className="wrap page-hero">
          <h1>Safe meetup guide</h1>
          <p className="lede">
            Almost every CampusBazaar deal is two students, one item and five minutes outside the library. These habits keep it that way.
          </p>
        </header>
        <article className="wrap">
          <div className="prose">
            <h2>Before you meet</h2>
            <ul>
              <li>
                <strong>Keep the conversation in CampusBazaar.</strong> Offers, counters and meetup plans stay on record in the chat, and you
                never have to hand out your phone number.
              </li>
              <li>
                <strong>Look at the seller card.</strong> Ratings, response rate and items sold tell you whether someone has done this before.
              </li>
              <li>
                <strong>Agree the price with an offer card,</strong> not just a message. An accepted offer reserves the item for you, so nobody
                else can turn up and buy it first.
              </li>
              <li>
                <strong>Ask for another photo if you&apos;re unsure,</strong> like the calculator switched on or the cycle&apos;s tyres.
                Genuine sellers won&apos;t mind.
              </li>
              <li>
                <strong>Slow down when someone rushes you.</strong> &ldquo;Three people are asking, pay now to hold it&rdquo; is the oldest
                trick there is.
              </li>
            </ul>

            <h2>Pick the spot</h2>
            <ul>
              <li>
                Meet somewhere busy on campus: the library entrance, the canteen, the main gate or a department lobby. Meetup cards suggest
                spots like these.
              </li>
              <li>Prefer daylight, and skip hostel rooms for a first meeting.</li>
              <li>Tell a friend where you&apos;re going, or bring one along for expensive things like cycles, laptops and phones.</li>
            </ul>

            <h2>Check the item</h2>
            <ul>
              <li>
                <strong>Electronics:</strong> switch it on, press every button, try the charger. For calculators, check the model number
                matches the listing; exam halls care about the exact model.
              </li>
              <li>
                <strong>Cycles:</strong> brakes, chain, gears and both tyres. Ask whether there&apos;s a lock and key.
              </li>
              <li>
                <strong>Books and notes:</strong> flip through for missing pages, heavy highlighting and the right edition.
              </li>
              <li>
                <strong>Coolers, fridges and kettles:</strong> ask the seller to run it before you meet, or meet near a plug point.
              </li>
            </ul>

            <h2>Paying</h2>
            <ul>
              <li>Pay only after you&apos;ve checked the item, directly to the seller, in cash or by UPI.</li>
              <li>
                <strong>Sellers: confirm UPI payments in your own bank app.</strong> A screenshot of a payment is not a payment.
              </li>
              <li>
                <strong>Never share an OTP or your UPI PIN.</strong> Receiving money never needs your PIN. If someone sends you a
                &ldquo;collect request&rdquo; to give you money, it&apos;s a scam.
              </li>
              <li>Don&apos;t pay an advance or &ldquo;token amount&rdquo; to someone you haven&apos;t met for an item you haven&apos;t seen.</li>
            </ul>
            <p className="callout">
              CampusBazaar never asks you for money, bank details or OTPs, and never handles payments. Anyone claiming to be from
              CampusBazaar and asking for these is not us.
            </p>

            <h2>If something goes wrong</h2>
            <ul>
              <li>
                <strong>Block</strong> the student from the chat menu. They can&apos;t message you any more, and their listings disappear from
                your feed.
              </li>
              <li>
                <strong>Report</strong> the listing or the student from its menu so it can be reviewed.
              </li>
              <li>If you feel unsafe on campus, go to your campus security desk.</li>
              <li>
                If you&apos;ve lost money to a scam, call the national cyber fraud helpline on <strong>1930</strong> as soon as you can, and
                file a complaint at{" "}
                <a href="https://cybercrime.gov.in" rel="noopener">
                  cybercrime.gov.in
                </a>
                . Reporting within hours gives the bank the best chance of stopping the transfer.
              </li>
            </ul>

            <h2>For sellers</h2>
            <ul>
              <li>Describe flaws honestly and photograph them. A scratch you mention is a scratch nobody argues about later.</li>
              <li>Accepting an offer reserves the item automatically. Mark it sold right after the handover so it leaves everyone&apos;s feed.</li>
              <li>
                Read what you&apos;re not allowed to sell in the <Link href="/terms#not-allowed">terms of use</Link>.
              </li>
            </ul>
          </div>
        </article>
      </main>
      <SiteFooter />
    </>
  );
}
