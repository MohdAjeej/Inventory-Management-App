import { NextResponse, type NextRequest } from "next/server";

/**
 * Sends signed-out visitors from the live dashboard to /login. The demo dashboard is public.
 * This is only a convenience redirect; the backend still authorises every request.
 */
export function middleware(req: NextRequest) {
  const { pathname, search } = req.nextUrl;
  if (pathname.startsWith("/dashboard/demo")) return NextResponse.next();
  if (!req.cookies.get("ip_rt")?.value) {
    const url = req.nextUrl.clone();
    url.pathname = "/login";
    url.search = `?next=${encodeURIComponent(pathname + search)}`;
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard", "/dashboard/:path*"],
};
