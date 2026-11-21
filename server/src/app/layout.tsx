import type { Metadata, Viewport } from "next";
import { Anek_Latin } from "next/font/google";
import { env } from "@/lib/env";
import { site } from "@/lib/site";
import "./globals.css";

// One family, three widths: expanded headlines, condensed price tags, normal body text.
const anek = Anek_Latin({
  subsets: ["latin", "latin-ext"],
  axes: ["wdth"],
  variable: "--font-anek",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL(env.siteUrl()),
  title: {
    default: `${site.name}: ${site.tagline.toLowerCase()}`,
    template: `%s | ${site.name}`,
  },
  description: site.description,
  applicationName: site.name,
  manifest: "/manifest.webmanifest",
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    siteName: site.name,
    locale: "en_IN",
    url: "/",
    title: `${site.name}: ${site.tagline.toLowerCase()}`,
    description: site.description,
  },
  twitter: { card: "summary_large_image" },
  formatDetection: { telephone: false },
};

export const viewport: Viewport = {
  themeColor: "#16213E",
  colorScheme: "light",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN" className={anek.variable}>
      <body>
        <a className="skip" href="#main">
          Skip to content
        </a>
        {children}
      </body>
    </html>
  );
}
