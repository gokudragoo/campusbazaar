import Link from "next/link";
import { Stamp } from "@/components/site/bits";
import { SiteFooter, SiteHeader } from "@/components/site/chrome";

export const metadata = { title: "Page not found" };

export default function NotFound() {
  return (
    <>
      <SiteHeader />
      <main id="main" className="wrap lost">
        <div>
          <Stamp>Sold</Stamp>
          <h1>This page has already gone</h1>
          <p>The link may be old, or the page has moved. Everything on CampusBazaar starts from the home page.</p>
          <div className="lost__actions">
            <Link className="btn btn--ink" href="/">
              Go to the home page
            </Link>
            <Link className="btn btn--line" href="/download">
              Download the app
            </Link>
          </div>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
