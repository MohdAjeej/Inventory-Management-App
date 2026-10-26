"use client";

import { CheckCircle2 } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { Notice } from "@/components/ui/states";
import { contactSchema, fieldErrorsOf } from "@/lib/validation";

type Status = { kind: "idle" } | { kind: "sending" } | { kind: "sent" } | { kind: "error"; message: string };

export function ContactForm() {
  const [f, setF] = useState({ name: "", email: "", company: "", topic: "question", message: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [status, setStatus] = useState<Status>({ kind: "idle" });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = contactSchema.safeParse(f);
    if (!parsed.success) {
      setErrors(fieldErrorsOf(parsed.error));
      return;
    }
    setErrors({});
    setStatus({ kind: "sending" });
    try {
      const res = await fetch("/api/contact", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(parsed.data) });
      const body = await res.json().catch(() => ({}));
      if (res.ok) setStatus({ kind: "sent" });
      else {
        if (body.fieldErrors) setErrors(body.fieldErrors);
        setStatus({ kind: "error", message: body.message ?? "Something went wrong. Please try again." });
      }
    } catch {
      setStatus({ kind: "error", message: "Couldn't reach the server. Check your connection and try again." });
    }
  };

  if (status.kind === "sent") {
    return (
      <div role="status" className="rounded-xl border border-[#b4dcb6] bg-success-bg p-8 text-center">
        <CheckCircle2 className="mx-auto size-10 text-success" aria-hidden />
        <h2 className="mt-3 text-lg font-semibold text-navy-950">Thanks, {f.name.split(" ")[0]} — message sent</h2>
        <p className="mt-1 text-sm text-ink-2">We&apos;ll reply to {f.email}.</p>
        <Button variant="secondary" className="mt-5" onClick={() => { setF({ name: "", email: "", company: "", topic: "question", message: "" }); setStatus({ kind: "idle" }); }}>Send another message</Button>
      </div>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4 rounded-xl border border-line bg-white p-6 shadow-[var(--shadow-card)]">
      {status.kind === "error" && <Notice tone="danger">{status.message}</Notice>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Your name" required error={errors.name}>{(p) => <Input {...p} autoComplete="name" value={f.name} onChange={set("name")} />}</Field>
        <Field label="Email" required error={errors.email}>{(p) => <Input {...p} type="email" autoComplete="email" value={f.email} onChange={set("email")} />}</Field>
        <Field label="Company" error={errors.company}>{(p) => <Input {...p} autoComplete="organization" value={f.company} onChange={set("company")} />}</Field>
        <Field label="Topic" required error={errors.topic}>
          {(p) => (
            <Select {...p} value={f.topic} onChange={set("topic")}>
              <option value="question">General question</option>
              <option value="demo">Request a walkthrough</option>
              <option value="support">Support for an existing workspace</option>
              <option value="other">Something else</option>
            </Select>
          )}
        </Field>
      </div>
      <Field label="Message" required error={errors.message}>{(p) => <Textarea {...p} rows={5} value={f.message} onChange={set("message")} maxLength={2000} />}</Field>
      <div className="flex items-center justify-between gap-4">
        <p className="text-xs text-muted">We only use your details to reply to you.</p>
        <Button type="submit" loading={status.kind === "sending"}>Send message</Button>
      </div>
    </form>
  );
}
