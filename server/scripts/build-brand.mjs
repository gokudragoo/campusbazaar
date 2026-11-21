/**
 * Generates every brand asset from one definition of the CampusBazaar mark: an upright marigold
 * price tag on its string that doubles as a college portico (pediment, round window, lintel,
 * columns). Run after changing the mark:
 *
 *   node scripts/build-brand.mjs
 *
 * Writes favicon.ico / icon.svg / apple-icon.png (Next.js file conventions), PWA icons, the
 * standalone mark SVGs, and web-sized copies of the README screenshots used on the site.
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const repo = join(root, "..");

const INK = "#16213E";
const MARIGOLD = "#F5B800";
const PAPER = "#F3F4F7";

// Geometry on a 64-unit grid, before the hang transform.
const TAG =
  "M32 5.5 Q33.6 5.5 34.9 6.5 L51 19.2 Q53 20.8 53 23.4 L53 54 Q53 59 48 59 L16 59 Q11 59 11 54 L11 23.4 Q11 20.8 13 19.2 L29.1 6.5 Q30.4 5.5 32 5.5 Z";
const HOLE = "M32 18 a4.6 4.6 0 1 0 0.001 0 Z";
const LINTEL = "M17 29 h30 v3 h-30 Z";
const COLUMNS = "M20.6 36 h3.4 v15 h-3.4 Z M30.3 36 h3.4 v15 h-3.4 Z M40 36 h3.4 v15 h-3.4 Z";
const STRING = "M32 22.6 C 30.5 12, 37 3.5, 47 0";
const BODY = `${TAG} ${HOLE} ${LINTEL} ${COLUMNS}`;
/** Hangs the tag slightly off-vertical and leaves room above for the string. */
const HANG = "rotate(-10 32 36) translate(6.4 9) scale(.8)";

function markGroup({ tag = MARIGOLD, string = INK, transform = HANG } = {}) {
  return (
    `<g transform="${transform}">` +
    `<path fill="${tag}" fill-rule="evenodd" d="${BODY}"/>` +
    `<path d="${STRING}" fill="none" stroke="${string}" stroke-width="2.4" stroke-linecap="round"/>` +
    `</g>`
  );
}

/** Rounded ink tile with the mark: favicon and app-icon look. */
const tileSvg = (rx = 14) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="${rx}" fill="${INK}"/>${markGroup({ string: PAPER })}</svg>`;

/** Full-bleed ink square with the mark inside the maskable safe zone (inner 80%). */
const maskableSvg = () =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" fill="${INK}"/>` +
  `<g transform="translate(32 32) scale(.78) translate(-32 -32)">${markGroup({ string: PAPER })}</g></svg>`;

/** Standalone mark for light backgrounds. */
const markSvg = () =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="8 4 48 58">${markGroup()}</svg>`;

/** Standalone mark for dark backgrounds. */
const markOnDarkSvg = () =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="8 4 48 58">${markGroup({ string: PAPER })}</svg>`;

const png = (svg, size) => sharp(Buffer.from(svg), { density: 72 * Math.ceil(size / 64) * 4 }).resize(size, size).png().toBuffer();

/** ICO container with embedded PNGs (supported by every current browser). */
function ico(images) {
  const header = Buffer.alloc(6 + images.length * 16);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(images.length, 4);
  let offset = header.length;
  images.forEach(({ size, data }, i) => {
    const entry = 6 + i * 16;
    header.writeUInt8(size >= 256 ? 0 : size, entry);
    header.writeUInt8(size >= 256 ? 0 : size, entry + 1);
    header.writeUInt8(0, entry + 2);
    header.writeUInt8(0, entry + 3);
    header.writeUInt16LE(1, entry + 4);
    header.writeUInt16LE(32, entry + 6);
    header.writeUInt32LE(data.length, entry + 8);
    header.writeUInt32LE(offset, entry + 12);
    offset += data.length;
  });
  return Buffer.concat([header, ...images.map((i) => i.data)]);
}

async function out(path, data) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, data);
  console.log("wrote", path.slice(repo.length + 1));
}

const app = join(root, "src", "app");
const pub = join(root, "public");

await out(join(app, "icon.svg"), tileSvg());
await out(join(app, "favicon.ico"), ico(await Promise.all([16, 32, 48].map(async (size) => ({ size, data: await png(tileSvg(), size) })))));
await out(join(app, "apple-icon.png"), await png(tileSvg(0), 180));
await out(join(pub, "icons", "icon-192.png"), await png(tileSvg(), 192));
await out(join(pub, "icons", "icon-512.png"), await png(tileSvg(), 512));
await out(join(pub, "icons", "maskable-512.png"), await png(maskableSvg(), 512));
await out(join(pub, "brand", "mark.svg"), markSvg());
await out(join(pub, "brand", "mark-on-dark.svg"), markOnDarkSvg());
await out(join(pub, "brand", "app-icon.svg"), tileSvg());

// Real emulator screenshots (docs/screenshots) re-encoded for the web.
const shots = ["03-gemini-draft", "04-listing-detail", "06-offer-accepted", "07-meetup-confirmed", "08-sold-and-rated", "09-campus-square", "05-chats", "12-profile"];
for (const name of shots) {
  const data = await sharp(join(repo, "docs", "screenshots", `${name}.png`)).resize(540).webp({ quality: 82 }).toBuffer();
  await out(join(pub, "shots", `${name}.webp`), data);
}
// The demo listing's photo (the Casio on the listing detail screen), for the hero negotiation.
await out(
  join(pub, "shots", "casio.webp"),
  await sharp(join(repo, "docs", "screenshots", "04-listing-detail.png"))
    .extract({ left: 140, top: 300, width: 800, height: 800 })
    .resize(240)
    .webp({ quality: 80 })
    .toBuffer(),
);
