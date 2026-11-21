/**
 * Central, lazy access to environment variables. Values are read on first use so that
 * `next build` never needs production secrets, and a missing variable fails loudly at the
 * call site instead of producing confusing downstream errors.
 */
function required(name: string): string {
  const value = process.env[name];
  if (!value || value.trim() === "") {
    throw new Error(`Missing required environment variable ${name}`);
  }
  return value.trim();
}

function optional(name: string, fallback: string): string {
  const value = process.env[name];
  return value && value.trim() !== "" ? value.trim() : fallback;
}

export const env = {
  databaseUrl: () => required("DATABASE_URL"),
  jwtSecret: () => required("JWT_SECRET"),
  nexconnAppKey: () => required("NEXCONN_APP_KEY"),
  nexconnAppSecret: () => required("NEXCONN_APP_SECRET"),
  nexconnApiBase: () => optional("NEXCONN_API_BASE", "https://api.sg-light-api.com").replace(/\/+$/, ""),
  nexconnApiBackup: () => optional("NEXCONN_API_BACKUP", "https://api-b.sg-light-api.com").replace(/\/+$/, ""),
  nexconnPrefix: () => optional("NEXCONN_ID_PREFIX", "cbz").replace(/[^a-zA-Z0-9]/g, ""),
  blobToken: () => required("BLOB_READ_WRITE_TOKEN"),
  geminiApiKey: () => process.env.GEMINI_API_KEY?.trim() || null,
  geminiModel: () => optional("GEMINI_MODEL", "gemini-3.5-flash-lite"),
  geminiFallbackModel: () => optional("GEMINI_FALLBACK_MODEL", "gemini-3.1-flash-lite"),
  apkUrl: () => process.env.APK_DOWNLOAD_URL?.trim() || null,
  /** Public origin of the website, for canonical URLs, Open Graph and the download QR code. */
  siteUrl: () => {
    const explicit = process.env.SITE_URL?.trim();
    const vercel = process.env.VERCEL_PROJECT_PRODUCTION_URL?.trim();
    return (explicit || (vercel ? `https://${vercel}` : "https://campusbazaar-api.vercel.app")).replace(/\/+$/, "");
  },
};
