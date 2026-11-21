import QRCode from "qrcode";
import type { ReactNode } from "react";
import { env } from "@/lib/env";
import { APK_PATH } from "@/lib/site";
import { PriceTag } from "./bits";

/** The kind of things that change hands on a campus, at the prices they actually go for. */
const WALL: [string, string][] = [
  ["₹650", "Casio fx-991EX"],
  ["₹280", "Engineering Drawing, N.D. Bhatt"],
  ["₹2,400", "Hercules cycle, 26 inch"],
  ["₹1,500", "Symphony air cooler"],
  ["₹150", "Lab coat, size M"],
  ["₹350", "HC Verma, both volumes"],
  ["₹4,200", "Mini fridge, 50 L"],
  ["₹220", "Electric kettle"],
  ["₹120", "DSA notes, 3rd sem"],
  ["₹600", "Mini drafter"],
  ["₹450", "Study lamp"],
  ["₹900", "Mattress, single"],
];

export function TagWall() {
  const row = (hidden: boolean) => (
    <ul className="wall__row" aria-hidden={hidden || undefined}>
      {WALL.map(([price, item]) => (
        <li key={item}>
          <PriceTag size="md">{price}</PriceTag>
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
  return (
    <section className="wall" aria-label="Things students sell on CampusBazaar">
      <div className="wall__track">
        {row(false)}
        {row(true)}
      </div>
    </section>
  );
}

/** QR code to the APK, for people reading the site on a laptop. */
export async function DownloadQr({ caption = "Scan with your phone to download" }: { caption?: string }) {
  const svg = await QRCode.toString(`${env.siteUrl()}${APK_PATH}`, {
    type: "svg",
    margin: 0,
    errorCorrectionLevel: "M",
    color: { dark: "#16213E", light: "#0000" },
  });
  return (
    <figure className="qr">
      <span className="qr__code" dangerouslySetInnerHTML={{ __html: svg }} />
      <figcaption>{caption}</figcaption>
    </figure>
  );
}

export function Faq({ items }: { items: { q: string; a: ReactNode }[] }) {
  return (
    <div className="faq">
      {items.map((item) => (
        <details key={item.q} className="faq__item">
          <summary>{item.q}</summary>
          <div className="faq__answer">{item.a}</div>
        </details>
      ))}
    </div>
  );
}
