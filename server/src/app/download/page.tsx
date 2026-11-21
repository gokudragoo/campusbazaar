import type { Metadata } from "next";
import { Phone } from "@/components/site/bits";
import { SiteFooter, SiteHeader } from "@/components/site/chrome";
import { DownloadQr } from "@/components/site/sections";
import { APK_PATH, apkRelease } from "@/lib/site";

export const metadata: Metadata = {
  title: "Download for Android",
  description: "Download the signed CampusBazaar APK for Android 8.0 or newer, and how to install it in a minute.",
  alternates: { canonical: "/download" },
};

export default function DownloadPage() {
  const { url, version } = apkRelease();
  return (
    <>
      <SiteHeader />
      <main id="main">
        <header className="wrap page-hero">
          <h1>Get CampusBazaar for Android</h1>
          <p className="lede">Free, signed, and free of ads and trackers. Install it, pick your campus, and you&apos;re in.</p>
        </header>
        <div className="wrap download">
          <div>
            <div className="download__card">
              {url ? (
                <a className="btn btn--ink btn--big" href={APK_PATH}>
                  Download the APK
                </a>
              ) : (
                <p>The download is being updated. Check back in a few minutes.</p>
              )}
              <p className="download__facts">
                {version ? (
                  <>
                    Version <b>{version}</b>
                    <br />
                  </>
                ) : null}
                Android 8.0 or newer
              </p>
              <DownloadQr caption="On a laptop? Scan this with your phone." />
            </div>

            <ol className="install">
              <li>
                <b>Download the file</b>
                <p>
                  Tap Download the APK on your phone. Chrome may warn that this type of file can harm your device; tap Download anyway.
                </p>
              </li>
              <li>
                <b>Open it and allow the install</b>
                <p>
                  Open the file from the notification or from Downloads. The first time, Android asks you to allow installs from your
                  browser: tap Settings, switch on Allow from this source, and go back.
                </p>
              </li>
              <li>
                <b>Install and sign up</b>
                <p>
                  Tap Install, open CampusBazaar and create an account with any email. Then search for your campus, or add it with its name
                  and city if nobody from your college has joined yet.
                </p>
              </li>
            </ol>
            <p className="download__facts">
              <b>Updating?</b> Download the new APK and install it over the old one. You stay signed in and your chats stay put.
            </p>
          </div>
          <Phone>
            <img src="/shots/01-welcome.webp" alt="The CampusBazaar welcome screen with price tags for a calculator, a textbook and a cycle" width={540} height={1200} />
          </Phone>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
