import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CampusBazaar",
  description: "Buy and sell used books, calculators, cycles and hostel gear with students on your campus.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
