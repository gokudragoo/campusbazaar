import type { ReactNode } from "react";

/** The marigold price tag from the app: notched left end, punched hole, condensed numerals. */
export function PriceTag({ children, size = "md", className = "" }: { children: ReactNode; size?: "sm" | "md" | "lg"; className?: string }) {
  return (
    <span className={`tag tag--${size} ${className}`}>
      <span className="tag__hole" aria-hidden="true" />
      {children}
    </span>
  );
}

/** Rubber stamp (SOLD, ACCEPTED, CONFIRMED) in stamp red. */
export function Stamp({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <span className={`stamp ${className}`}>{children}</span>;
}

/** A phone body that frames app screens. */
export function Phone({ children, className = "", label }: { children: ReactNode; className?: string; label?: string }) {
  return (
    <div className={`phone ${className}`} role={label ? "img" : undefined} aria-label={label}>
      <div className="phone__screen">{children}</div>
    </div>
  );
}

export function rupees(amount: number): string {
  return `₹${amount.toLocaleString("en-IN")}`;
}
