import Link from "next/link";
import { site } from "@/lib/site";
import { Logo } from "./brand";

const NAV = [
  { href: "/#how", label: "How it works" },
  { href: "/#square", label: "Campus Square" },
  { href: "/safety", label: "Safety" },
  { href: "/#nexconn", label: "Built on Nexconn" },
];

export function SiteHeader() {
  return (
    <header className="site-header">
      <div className="wrap site-header__row">
        <Logo />
        <nav className="site-nav" aria-label="Main">
          {NAV.map((item) => (
            <Link key={item.href} href={item.href}>
              {item.label}
            </Link>
          ))}
        </nav>
        <Link href="/download" className="btn btn--ink btn--small">
          Get the app
        </Link>
      </div>
    </header>
  );
}

const FOOTER = [
  {
    title: "App",
    links: [
      { href: "/download", label: "Download for Android" },
      { href: "/#how", label: "How a deal works" },
      { href: "/#square", label: "Campus Square" },
      { href: "/#faq", label: "Questions" },
    ],
  },
  {
    title: "Trust",
    links: [
      { href: "/safety", label: "Safe meetup guide" },
      { href: "/privacy", label: "Privacy policy" },
      { href: "/terms", label: "Terms of use" },
    ],
  },
  {
    title: "Project",
    links: [
      { href: site.github, label: "Source on GitHub" },
      { href: site.issues, label: "Report a problem" },
      { href: "/api/health", label: "API status" },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="wrap site-footer__grid">
        <div className="site-footer__brand">
          <Logo onDark />
          <p>The marketplace inside your college. Built for students in India, priced in rupees.</p>
        </div>
        {FOOTER.map((col) => (
          <div key={col.title}>
            <h2 className="site-footer__title">{col.title}</h2>
            <ul>
              {col.links.map((l) => (
                <li key={l.href}>
                  {l.href.startsWith("http") ? (
                    <a href={l.href} rel="noopener">
                      {l.label}
                    </a>
                  ) : (
                    <Link href={l.href}>{l.label}</Link>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="wrap site-footer__base">
        <span>© 2026 CampusBazaar</span>
        <span>Real-time chat by Nexconn, Singapore data center</span>
      </div>
    </footer>
  );
}
