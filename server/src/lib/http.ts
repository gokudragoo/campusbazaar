import { NextResponse } from "next/server";
import { ZodError, type ZodType } from "zod";

/** An error that maps directly onto an HTTP response with a user-facing message. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export const badRequest = (message: string, code = "bad_request") => new ApiError(400, code, message);
export const unauthorized = (message = "Sign in to continue.") => new ApiError(401, "unauthorized", message);
export const forbidden = (message = "You can't do that.") => new ApiError(403, "forbidden", message);
export const notFound = (message = "Not found.") => new ApiError(404, "not_found", message);
export const conflict = (message: string, code = "conflict") => new ApiError(409, code, message);
export const tooMany = (message = "Too many requests. Try again in a few minutes.") => new ApiError(429, "rate_limited", message);

export function json<T>(data: T, init?: ResponseInit): NextResponse {
  const res = NextResponse.json(data, init);
  if (!res.headers.has("Cache-Control")) res.headers.set("Cache-Control", "private, no-cache");
  return res;
}

export function errorResponse(err: unknown): NextResponse {
  if (err instanceof ApiError) {
    return json({ error: { code: err.code, message: err.message } }, { status: err.status });
  }
  if (err instanceof ZodError) {
    const first = err.issues[0];
    const field = first?.path.join(".") ?? "";
    const message = first ? `${field ? `${field}: ` : ""}${first.message}` : "Invalid request.";
    return json({ error: { code: "validation_error", message } }, { status: 400 });
  }
  console.error("[api] unhandled error", err);
  return json({ error: { code: "internal", message: "Something went wrong on our side. Try again." } }, { status: 500 });
}

type Handler<C> = (req: Request, ctx: C) => Promise<Response>;

/** Wraps a route handler so every thrown error becomes a consistent JSON error body. */
export function route<C>(handler: Handler<C>): Handler<C> {
  return async (req, ctx) => {
    try {
      return await handler(req, ctx);
    } catch (err) {
      return errorResponse(err);
    }
  };
}

export async function parseBody<T>(req: Request, schema: ZodType<T>): Promise<T> {
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    throw badRequest("Request body must be valid JSON.");
  }
  return schema.parse(raw);
}

export function parseQuery<T>(req: Request, schema: ZodType<T>): T {
  const params = Object.fromEntries(new URL(req.url).searchParams.entries());
  return schema.parse(params);
}

export type RouteParams<P extends Record<string, string>> = { params: Promise<P> };
