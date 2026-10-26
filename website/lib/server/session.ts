import "server-only";
import type { NextResponse } from "next/server";
import type { AuthResponse } from "@/lib/api/types";

/**
 * Server-only session handling. Tokens from the Spring backend are kept in httpOnly cookies and
 * never reach browser JavaScript. Every backend call is made from the server with
 * `Authorization: Bearer <access token>`.
 */

export const ACCESS_COOKIE = "ip_at";
export const REFRESH_COOKIE = "ip_rt";
const REFRESH_MAX_AGE = 60 * 60 * 24 * 30; // backend default JWT_REFRESH_DAYS = 30

export function apiBaseUrl(): string {
  const base = process.env.API_BASE_URL;
  if (!base) throw new Error("API_BASE_URL is not set. Copy .env.example to .env.local and set it.");
  return base.replace(/\/+$/, "");
}

function secureCookies(): boolean {
  const flag = process.env.SESSION_COOKIE_SECURE;
  if (flag === "true") return true;
  if (flag === "false") return false;
  return process.env.NODE_ENV === "production";
}

export function setSessionCookies(res: NextResponse, auth: Pick<AuthResponse, "accessToken" | "refreshToken" | "expiresIn">) {
  const common = { httpOnly: true, sameSite: "lax" as const, secure: secureCookies(), path: "/" };
  // Expire the access cookie a little early so we refresh before the backend rejects it.
  res.cookies.set(ACCESS_COOKIE, auth.accessToken, { ...common, maxAge: Math.max(auth.expiresIn - 60, 60) });
  res.cookies.set(REFRESH_COOKIE, auth.refreshToken, { ...common, maxAge: REFRESH_MAX_AGE });
}

export function clearSessionCookies(res: NextResponse) {
  res.cookies.set(ACCESS_COOKIE, "", { path: "/", maxAge: 0 });
  res.cookies.set(REFRESH_COOKIE, "", { path: "/", maxAge: 0 });
}

/**
 * Refresh tokens rotate on every use and replaying a used one revokes the whole session
 * (docs/API.md). Browsers often fire several requests at once with the same stale cookie, so
 * concurrent refreshes for one token share a single backend call, and the result is remembered
 * briefly for requests that arrive just after. This guard is per server process; deployments
 * with several instances should use sticky sessions or a shared store.
 */
const inflight = new Map<string, { promise: Promise<AuthResponse | null>; expires: number }>();

export function refreshSession(refreshToken: string): Promise<AuthResponse | null> {
  const now = Date.now();
  for (const [key, entry] of inflight) if (entry.expires < now) inflight.delete(key);
  const existing = inflight.get(refreshToken);
  if (existing) return existing.promise;

  const promise = (async () => {
    try {
      const res = await fetch(`${apiBaseUrl()}/api/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
        cache: "no-store",
      });
      if (!res.ok) return null;
      return (await res.json()) as AuthResponse;
    } catch {
      return null;
    }
  })();
  inflight.set(refreshToken, { promise, expires: now + 30_000 });
  return promise;
}
