import type { NextRequest } from "next/server";
import { forwardAuth } from "@/lib/server/auth-handler";

export const dynamic = "force-dynamic";

/** Switching business issues new tokens scoped to the chosen membership. */
export function POST(req: NextRequest) {
  return forwardAuth(req, "/api/auth/switch-business", { authorized: true });
}
