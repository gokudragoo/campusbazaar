import type { MetadataRoute } from "next";
import { env } from "@/lib/env";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/", disallow: ["/api/", "/download/apk"] },
    sitemap: `${env.siteUrl()}/sitemap.xml`,
  };
}
