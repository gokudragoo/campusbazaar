import { neon } from "@neondatabase/serverless";
import { drizzle, type NeonHttpDatabase } from "drizzle-orm/neon-http";
import * as schema from "./schema";
import { env } from "../env";

let instance: NeonHttpDatabase<typeof schema> | null = null;

/** Lazily-created Drizzle client over Neon's HTTP driver (stateless, ideal for serverless). */
export function db(): NeonHttpDatabase<typeof schema> {
  if (!instance) {
    instance = drizzle(neon(env.databaseUrl()), { schema });
  }
  return instance;
}

export { schema };
