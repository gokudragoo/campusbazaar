import Link from "next/link";

// Same geometry as scripts/build-brand.mjs (the source of truth for the generated icon files).
const BODY =
  "M32 5.5 Q33.6 5.5 34.9 6.5 L51 19.2 Q53 20.8 53 23.4 L53 54 Q53 59 48 59 L16 59 Q11 59 11 54 L11 23.4 Q11 20.8 13 19.2 L29.1 6.5 Q30.4 5.5 32 5.5 Z " +
  "M32 18 a4.6 4.6 0 1 0 0.001 0 Z M17 29 h30 v3 h-30 Z " +
  "M20.6 36 h3.4 v15 h-3.4 Z M30.3 36 h3.4 v15 h-3.4 Z M40 36 h3.4 v15 h-3.4 Z";
const STRING = "M32 22.6 C 30.5 12, 37 3.5, 47 0";

/** The CampusBazaar mark: a price tag on its string that is also a college portico. */
export function Mark({ size = 32, string = "currentColor", className }: { size?: number; string?: string; className?: string }) {
  return (
    <svg className={className} width={size} height={size * (58 / 48)} viewBox="8 4 48 58" aria-hidden="true" focusable="false">
      <g transform="rotate(-10 32 36) translate(6.4 9) scale(.8)">
        <path fill="var(--marigold)" fillRule="evenodd" d={BODY} />
        <path d={STRING} fill="none" stroke={string} strokeWidth="2.4" strokeLinecap="round" />
      </g>
    </svg>
  );
}

export function Logo({ onDark = false }: { onDark?: boolean }) {
  return (
    <Link href="/" className={`logo${onDark ? " logo--dark" : ""}`} aria-label="CampusBazaar home">
      <Mark size={28} className="logo__mark" />
      <span className="logo__word">CampusBazaar</span>
    </Link>
  );
}
