import type { NextRequest } from "next/server";
import { forwardAuth } from "@/lib/server/auth-handler";

export const dynamic = "force-dynamic";

export function POST(req: NextRequest) {
  return forwardAuth(req, "/api/auth/login");
}
