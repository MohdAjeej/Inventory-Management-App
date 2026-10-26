import { NextResponse } from "next/server";
import { contactSchema, fieldErrorsOf } from "@/lib/validation";

export const dynamic = "force-dynamic";

/**
 * Contact form endpoint. The backend has no contact/email feature, so delivery goes to an
 * optional webhook (CONTACT_WEBHOOK_URL — e.g. a CRM or Slack incoming webhook). Without one we
 * say so honestly instead of pretending the message was sent.
 */
export async function POST(req: Request) {
  let data: unknown;
  try {
    data = await req.json();
  } catch {
    return NextResponse.json({ code: "BAD_REQUEST", message: "Invalid request" }, { status: 400 });
  }
  const parsed = contactSchema.safeParse(data);
  if (!parsed.success) {
    return NextResponse.json(
      { code: "VALIDATION_FAILED", message: "Please fix the highlighted fields.", fieldErrors: fieldErrorsOf(parsed.error) },
      { status: 400 },
    );
  }

  const webhook = process.env.CONTACT_WEBHOOK_URL;
  if (!webhook) {
    return NextResponse.json(
      {
        code: "NOT_CONFIGURED",
        message: "Message delivery isn't set up on this site yet, so your message was not sent. Please email us instead.",
      },
      { status: 503 },
    );
  }

  try {
    const res = await fetch(webhook, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...parsed.data, receivedAt: new Date().toISOString() }),
    });
    if (!res.ok) throw new Error(String(res.status));
  } catch {
    return NextResponse.json(
      { code: "DELIVERY_FAILED", message: "We couldn't send your message right now. Please try again shortly." },
      { status: 502 },
    );
  }
  return NextResponse.json({ ok: true });
}
