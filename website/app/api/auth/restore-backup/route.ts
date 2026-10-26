import type { NextRequest } from "next/server";
import { forwardAuth } from "@/lib/server/auth-handler";

export const dynamic = "force-dynamic";

/**
 * Restoring a backup creates a NEW business from the file and returns tokens signed into it
 * (BackupController). The tokens go into httpOnly cookies like any other sign-in.
 */
export function POST(req: NextRequest) {
  return forwardAuth(req, "/api/backup/restore", { authorized: true });
}
