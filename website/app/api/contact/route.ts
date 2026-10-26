import { NextResponse } from "next/server";
import { contactSchema, fieldErrorsOf } from "@/lib/validation";

export const dynamic = "force-dynamic";

type Contact = { name: string; email: string; company?: string; topic: string; message: string };

/** Slack incoming webhooks reject payloads without `text`; every other webhook gets the raw JSON. */
function webhookPayload(url: URL, c: Contact, receivedAt: string) {
  if (url.hostname === "hooks.slack.com") {
    const lines = [
      `*New contact message* (${c.topic})`,
      `*From:* ${c.name} <${c.email}>${c.company ? ` — ${c.company}` : ""}`,
      "",
      c.message,
    ];
    return { text: lines.join("\n") };
  }
  return { ...c, receivedAt };
}

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
    // Contact details are personal data: only send them over HTTPS, and never follow a redirect elsewhere.
    const url = new URL(webhook);
    if (url.protocol !== "https:") throw new Error("Webhook must use HTTPS");
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(webhookPayload(url, parsed.data, new Date().toISOString())),
      redirect: "error",
      signal: AbortSignal.timeout(10_000),
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
