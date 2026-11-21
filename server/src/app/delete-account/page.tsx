import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter, SiteHeader } from "@/components/site/chrome";
import { DeleteAccountForm } from "./DeleteAccountForm";

export const metadata: Metadata = {
  title: "Delete your account",
  description: "Delete your CampusBazaar account from the web, without the app.",
  alternates: { canonical: "/delete-account" },
  robots: { index: false },
};

export default function DeleteAccountPage() {
  return (
    <>
      <SiteHeader />
      <main id="main">
        <header className="wrap page-hero">
          <h1>Delete your account</h1>
          <p className="lede">
            Still have the app? It&apos;s quicker there: Profile, then the menu, then Delete account. Otherwise, sign in below to delete it
            from here.
          </p>
        </header>
        <div className="wrap">
          <div className="prose">
            <DeleteAccountForm />
            <p className="form-note">
              This can&apos;t be undone. The <Link href="/privacy#deletion">privacy policy</Link> lists exactly what&apos;s removed.
            </p>
          </div>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
