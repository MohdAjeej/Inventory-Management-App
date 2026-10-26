import { NextResponse, type NextRequest } from "next/server";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  apiBaseUrl,
  clearSessionCookies,
  refreshSession,
  setSessionCookies,
} from "@/lib/server/session";

/**
 * Same-origin proxy to the Spring backend: /api/backend/<path> → ${API_BASE_URL}/api/<path>.
 *
 * - Avoids CORS (the backend has no CORS configuration) without changing the backend.
 * - Attaches the access token from the httpOnly cookie; refreshes once on 401.
 * - Endpoints that issue or revoke tokens are not proxied here; they have dedicated
 *   handlers under /api/auth that keep tokens out of the browser.
 */

export const dynamic = "force-dynamic";

const BLOCKED = [
  /^auth\/(login|register|refresh|logout|switch-business|accept-invitation)$/,
];
/** Backup restore issues tokens, so it goes through /api/auth/restore-backup instead. */
const BLOCKED_POST = [/^backup\/restore$/];

type Ctx = { params: Promise<{ path: string[] }> };

const notFound = () => NextResponse.json({ status: 404, code: "NOT_FOUND", message: "Not found" }, { status: 404 });

async function handle(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  // fetch() normalises dot segments, so "auth/../auth/login" would slip past the blocklist and
  // "../x" would escape /api. Reject them outright rather than trying to resolve them.
  if (path.some((seg) => seg === "" || seg === "." || seg === "..")) return notFound();
  const joined = path.map(encodeURIComponent).join("/");
  if (BLOCKED.some((re) => re.test(joined)) || (req.method === "POST" && BLOCKED_POST.some((re) => re.test(joined)))) {
    return notFound();
  }

  let base: string;
  try {
    base = apiBaseUrl();
  } catch (err) {
    return NextResponse.json(
      { status: 500, code: "CONFIG_ERROR", message: (err as Error).message },
      { status: 500 },
    );
  }

  const target = `${base}/api/${joined}${req.nextUrl.search}`;
  // Defence in depth: after URL normalisation the request must still go to exactly the path we checked.
  if (new URL(target).pathname !== `${new URL(base).pathname.replace(/\/+$/, "")}/api/${joined}`) return notFound();
  const body = req.method === "GET" || req.method === "HEAD" ? undefined : await req.arrayBuffer();
  const contentType = req.headers.get("content-type");

  const send = (token: string | undefined) =>
    fetch(target, {
      method: req.method,
      headers: {
        Accept: req.headers.get("accept") ?? "application/json",
        ...(contentType ? { "Content-Type": contentType } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body && body.byteLength > 0 ? body : undefined,
      cache: "no-store",
      redirect: "manual",
    });

  let access = req.cookies.get(ACCESS_COOKIE)?.value;
  const refresh = req.cookies.get(REFRESH_COOKIE)?.value;
  let renewed: Awaited<ReturnType<typeof refreshSession>> = null;

  // Access cookie expired (it is set to expire slightly before the token) — refresh first.
  if (!access && refresh) {
    renewed = await refreshSession(refresh);
    access = renewed?.accessToken;
  }

  let upstream: Response;
  try {
    upstream = await send(access);
    if (upstream.status === 401 && refresh && !renewed) {
      renewed = await refreshSession(refresh);
      if (renewed) upstream = await send(renewed.accessToken);
    }
  } catch {
    return NextResponse.json(
      { status: 502, code: "BACKEND_UNREACHABLE", message: "The inventory server is not reachable. Is the backend running?" },
      { status: 502 },
    );
  }

  const headers = new Headers();
  for (const name of ["content-type", "content-disposition", "cache-control"]) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  const res = new NextResponse(upstream.status === 204 ? null : upstream.body, { status: upstream.status, headers });

  if (renewed) setSessionCookies(res, renewed);
  else if (upstream.status === 401 && refresh) clearSessionCookies(res);
  return res;
}

export { handle as GET, handle as POST, handle as PUT, handle as DELETE };
