import Link from "next/link";
import { Phone } from "@/components/site/bits";
import { SiteFooter, SiteHeader } from "@/components/site/chrome";
import { DealFlow } from "@/components/site/DealFlow";
import { HaggleDemo } from "@/components/site/HaggleDemo";
import { DownloadQr, Faq, TagWall } from "@/components/site/sections";
import { SUBCHANNELS } from "@/lib/campus";
import { APK_PATH, site } from "@/lib/site";

const SAFETY = [
  {
    title: "Meet where people are",
    body: "Meetup cards suggest public spots on your campus, like the library entrance, the canteen or the main gate. Daylight beats a dark parking lot.",
  },
  {
    title: "Pay after you've checked it",
    body: "CampusBazaar never asks you for money. Look the item over, switch it on, then pay the seller directly in cash or by UPI.",
  },
  {
    title: "Block in one tap",
    body: "A blocked student can't message you, and their listings and wanted posts disappear from your feed, and yours from theirs.",
  },
  {
    title: "Report what looks wrong",
    body: "Report a listing or a student from the menu, or hide a listing you never want to see again.",
  },
];

const NEXCONN: { see: string; feature: string; how: React.ReactNode }[] = [
  {
    see: "Chat with seller",
    feature: "Direct channels",
    how: "One channel per pair of students. Each item's negotiation is anchored by its listing card.",
  },
  {
    see: "Offer, counter and accept cards",
    feature: "Custom messages and message metadata",
    how: (
      <>
        <code>cbz:offer</code> cards keep their state in message metadata, so a tap on Accept updates the card on both phones.
      </>
    ),
  },
  {
    see: "Meetup cards and map pins",
    feature: "Custom and location messages",
    how: "A spot and a time to confirm, plus an optional pin with your current position.",
  },
  {
    see: "Campus Square",
    feature: "Community channel and subchannels",
    how: "Created and joined from our server when you pick a campus, then used live in the app.",
  },
  {
    see: "Activity inbox",
    feature: "System channel notices",
    how: "Reserved, sold, rated and wanted-post replies arrive from the CampusBazaar bot.",
  },
  {
    see: "Seen ticks, typing, unread badges",
    feature: "Read receipts, typing status, unread counts",
    how: "The chat list and tab badges stay in step with what you've actually read.",
  },
  {
    see: "Photos in chat",
    feature: "Image messages",
    how: "Uploaded through the SDK with a progress indicator.",
  },
  {
    see: "Block a student",
    feature: "User blocklist",
    how: "Nexconn stops their messages; our server hides their listings both ways.",
  },
  {
    see: "Stays signed in",
    feature: "Access tokens with refresh",
    how: "If Nexconn rejects a token, the app gets a fresh one from our server and reconnects quietly.",
  },
];

const FAQ = [
  {
    q: "Is CampusBazaar free?",
    a: "Yes. There are no listing fees and no commission. CampusBazaar never handles money: you pay the seller directly when you meet, in cash or by UPI.",
  },
  {
    q: "Which colleges can use it?",
    a: "Any college. Search for yours when you sign up. If it isn't there yet, add it with its name and city. Spellings like “I.I.T. Delhi” and “IIT delhi” end up as the same campus, so your batch won't split across duplicates.",
  },
  {
    q: "Is there an iPhone app?",
    a: "Not yet. CampusBazaar is Android only for now and needs Android 8.0 or newer.",
  },
  {
    q: "Why an APK instead of the Play Store?",
    a: (
      <>
        CampusBazaar is distributed as a signed APK for its first releases. When you open the file, Android asks you once to allow installs
        from your browser. The <Link href="/download">download page</Link> walks you through it.
      </>
    ),
  },
  {
    q: "Who can see my details?",
    a: "Students see your name, photo, hostel, department and ratings. Your email stays private, and CampusBazaar never asks for your phone number. You chat inside the app.",
  },
  {
    q: "Do I need a college email address?",
    a: "No. Any email works. You choose your campus after you sign up, and you can switch campus later from your profile.",
  },
  {
    q: "How does the price suggestion work?",
    a: "When you add photos, they're sent to Google Gemini, which suggests a title, category, condition, description and a fair used price range in rupees. Nothing is published until you tap Publish, and the form works just as well if you'd rather type everything yourself.",
  },
  {
    q: "Can I delete my account?",
    a: (
      <>
        Yes, from Profile in the app. Your listings, wanted posts and saved items are removed. The steps are in the{" "}
        <Link href="/privacy#deletion">privacy policy</Link>.
      </>
    ),
  },
];

export default function Home() {
  return (
    <>
      <SiteHeader />
      <main id="main">
        <section className="hero">
          <div className="wrap hero__grid">
            <div className="hero__copy">
              <h1 className="display">Buy and sell with students on your campus</h1>
              <p className="lede">
                Textbooks, calculators, cycles, coolers and notes from people a short walk away. Chat, haggle with offer cards, and meet at a
                safe spot on campus.
              </p>
              <div className="hero__actions">
                <a href={APK_PATH} className="btn btn--ink btn--big">
                  Download for Android
                </a>
                <Link href="#how" className="btn btn--line btn--big">
                  See how a deal works
                </Link>
              </div>
              <p className="hero__fine">Free, with no commission. Needs Android 8.0 or newer.</p>
            </div>
            <HaggleDemo />
          </div>
        </section>

        <TagWall />

        <section id="how" className="section">
          <div className="wrap">
            <header className="section__head">
              <h2>How a deal goes</h2>
              <p>From the first photo to the handover, everything happens inside the app.</p>
            </header>
            <DealFlow />
          </div>
        </section>

        <section id="square" className="section section--white">
          <div className="wrap split">
            <div>
              <h2>Every campus gets its own Square</h2>
              <p className="lede">
                Picking your campus drops you into its Campus Square: five open channels for everything that isn&apos;t a listing.
              </p>
              <ul className="channels">
                {SUBCHANNELS.map((sub) => (
                  <li key={sub.id}>
                    <b>#{sub.name}</b>
                    <span>{sub.description}</span>
                  </li>
                ))}
              </ul>
              <div className="wanted">
                <h3>Can&apos;t find it? Post it as Wanted.</h3>
                <p>
                  <q>Need an Engineering Graphics drafter by Monday.</q> Anyone on campus can post a wanted request, and sellers answer with
                  one of their listings or a quick note, which opens a chat.
                </p>
              </div>
            </div>
            <Phone>
              <img src="/shots/09-campus-square.webp" alt="The #general channel in Campus Square, with students sharing an auto to the city centre" width={540} height={1200} loading="lazy" />
            </Phone>
          </div>
        </section>

        <section id="safety" className="section">
          <div className="wrap">
            <header className="section__head">
              <h2>Made for meeting in person</h2>
              <p>Every deal ends with two students handing something over, so the app is built around doing that safely.</p>
            </header>
            <div className="points">
              {SAFETY.map((p) => (
                <div key={p.title} className="point">
                  <h3>{p.title}</h3>
                  <p>{p.body}</p>
                </div>
              ))}
            </div>
            <p className="section__more">
              <Link href="/safety" className="text-link">
                Read the safe meetup guide
              </Link>
            </p>
          </div>
        </section>

        <section id="nexconn" className="section section--ink">
          <div className="wrap">
            <header className="section__head">
              <h2>Built on Nexconn</h2>
              <p>
                Every chat, offer card, campus channel and notice in CampusBazaar runs on the Nexconn Chat SDK for Android and the Nexconn
                Platform Chat API, in Nexconn&apos;s Singapore data center. The App Secret stays on our server; the app only ever holds an
                access token.
              </p>
            </header>
            <table className="nx">
              <thead>
                <tr>
                  <th scope="col">In the app</th>
                  <th scope="col">Nexconn feature</th>
                  <th scope="col">How it&apos;s used</th>
                </tr>
              </thead>
              <tbody>
                {NEXCONN.map((row) => (
                  <tr key={row.see}>
                    <td className="nx__see">{row.see}</td>
                    <td className="nx__feature">{row.feature}</td>
                    <td className="nx__how">{row.how}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="nx-links">
              <a className="text-link" href={`${site.github}#how-this-uses-nexconn`} rel="noopener">
                Read the integration notes
              </a>
              <a className="text-link" href={site.github} rel="noopener">
                Browse the source on GitHub
              </a>
            </p>
          </div>
        </section>

        <section id="faq" className="section">
          <div className="wrap">
            <header className="section__head">
              <h2>Questions</h2>
            </header>
            <Faq items={FAQ} />
          </div>
        </section>

        <section className="cta">
          <div className="wrap cta__row">
            <div>
              <h2>Got something you&apos;re done with?</h2>
              <p>Someone on your campus is looking for it.</p>
            </div>
            <div className="cta__actions">
              <a href={APK_PATH} className="btn btn--ink btn--big">
                Download for Android
              </a>
              <DownloadQr caption="Or scan this with your phone" />
            </div>
          </div>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}
