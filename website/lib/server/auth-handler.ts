import "server-only";
import { NextResponse, type NextRequest } from "next/server";
import type { AuthResponse } from "@/lib/api/types";
import { ACCESS_COOKIE, REFRESH_COOKIE, apiBaseUrl, refreshSession, setSessionCookies } from "./session";

/**
 * Forwards a token-issuing auth call to the backend, stores the tokens in httpOnly cookies and
 * returns only the user profile to the browser.
 *
 * The body is passed through unchanged (no parse/re-serialise), so large backup files are not
 * duplicated in memory and numbers keep their exact text. With `authorized`, an expired access
 * cookie is refreshed first — and once more on a 401 — like the /api/backend proxy does.
 */
export async function forwardAuth(req: NextRequest, backendPath: string, opts: { authorized?: boolean } = {}) {
  const body = await req.text();
  if (!body.trim()) {
    return NextResponse.json({ status: 400, code: "BAD_REQUEST", message: "Invalid request body" }, { status: 400 });
  }

  let access = opts.authorized ? req.cookies.get(ACCESS_COOKIE)?.value : undefined;
  const refresh = opts.authorized ? req.cookies.get(REFRESH_COOKIE)?.value : undefined;
  let renewed: AuthResponse | null = null;
  if (opts.authorized && !access && refresh) {
    renewed = await refreshSession(refresh);
    access = renewed?.accessToken;
  }

  const send = (token: string | undefined) =>
    fetch(`${apiBaseUrl()}${backendPath}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body,
      cache: "no-store",
    });

  let upstream: Response;
  try {
    upstream = await send(access);
    if (upstream.status === 401 && refresh && !renewed) {
      renewed = await refreshSession(refresh);
      if (renewed) upstream = await send(renewed.accessToken);
    }
  } catch (err) {
    const message =
      (err as Error).message?.includes("API_BASE_URL")
        ? (err as Error).message
        : "The inventory server is not reachable. Is the backend running?";
    return NextResponse.json({ status: 502, code: "BACKEND_UNREACHABLE", message }, { status: 502 });
  }

  const text = await upstream.text();
  if (!upstream.ok) {
    const res = new NextResponse(text || null, {
      status: upstream.status,
      headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" },
    });
    // The call failed, but a refresh may have rotated the tokens; keep the new ones.
    if (renewed) setSessionCookies(res, renewed);
    return res;
  }

  let auth: AuthResponse;
  try {
    auth = JSON.parse(text) as AuthResponse;
    if (!auth?.accessToken || !auth.refreshToken) throw new Error("missing tokens");
  } catch {
    return NextResponse.json(
      { status: 502, code: "BAD_GATEWAY", message: "The server sent an unexpected response. Check that API_BASE_URL points to the inventory backend." },
      { status: 502 },
    );
  }
  const res = NextResponse.json({ user: auth.user });
  setSessionCookies(res, auth);
  return res;
}
