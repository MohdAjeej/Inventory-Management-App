"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { Notice } from "@/components/ui/states";
import type { ApiErrorBody, InvitationPreview } from "@/lib/api/types";
import { date } from "@/lib/format";
import { ROLE_LABELS } from "@/lib/permissions";
import { acceptInvitationSchema, fieldErrorsOf, loginSchema, registerAdminSchema, registerBusinessSchema } from "@/lib/validation";

async function postAuth(path: string, body: unknown): Promise<{ ok: true } | { ok: false; error: ApiErrorBody }> {
  try {
    const res = await fetch(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    if (res.ok) return { ok: true };
    const error = (await res.json().catch(() => null)) as ApiErrorBody | null;
    return { ok: false, error: error ?? { status: res.status, code: "HTTP_" + res.status, message: "Something went wrong. Please try again." } };
  } catch {
    return { ok: false, error: { status: 0, code: "NETWORK_ERROR", message: "Couldn't reach the server. Check your connection." } };
  }
}

/** Only allow same-site relative redirects after sign-in. */
function safeNext(next: string | null) {
  return next && next.startsWith("/dashboard") && !next.startsWith("//") ? next : "/dashboard";
}

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [f, setF] = useState({ email: "", password: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = loginSchema.safeParse(f);
    if (!parsed.success) return setErrors(fieldErrorsOf(parsed.error));
    setErrors({});
    setBusy(true);
    setFormError(undefined);
    const res = await postAuth("/api/auth/login", parsed.data);
    if (res.ok) {
      router.replace(safeNext(params.get("next")));
      router.refresh();
    } else {
      setFormError(res.error.status === 401 ? "Email or password is incorrect." : res.error.message);
      setErrors(res.error.fieldErrors ?? {});
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      {params.get("expired") && <Notice tone="warning">Your session ended. Please sign in again.</Notice>}
      {params.get("changed") && <Notice tone="success">Password changed. Sign in with your new password.</Notice>}
      {formError && <Notice tone="danger">{formError}</Notice>}
      <Field label="Email" error={errors.email}>{(p) => <Input {...p} type="email" autoComplete="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />}</Field>
      <Field label="Password" error={errors.password}>{(p) => <Input {...p} type="password" autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />}</Field>
      <Button type="submit" className="w-full" loading={busy}>Sign in</Button>
      <p className="text-center text-sm text-muted">
        Got an invitation code? <Link href="/join" className="font-medium text-brand-700 hover:underline">Join a business</Link>
      </p>
      <p className="text-center text-xs text-muted">Forgot your password? Ask your business admin; password reset by email isn&apos;t available yet.</p>
    </form>
  );
}

export function SignupForm() {
  const router = useRouter();
  const [step, setStep] = useState<1 | 2>(1);
  const [biz, setBiz] = useState({ businessName: "", businessType: "", businessPhone: "", city: "", country: "" });
  const [admin, setAdmin] = useState({ fullName: "", email: "", phone: "", password: "", confirm: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const next = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = registerBusinessSchema.safeParse(biz);
    if (!parsed.success) return setErrors(fieldErrorsOf(parsed.error));
    setErrors({});
    setStep(2);
  };
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = registerAdminSchema.safeParse(admin);
    const errs = parsed.success ? {} : fieldErrorsOf(parsed.error);
    if (admin.password !== admin.confirm) errs.confirm = "Passwords don't match";
    if (Object.keys(errs).length) return setErrors(errs);
    setBusy(true);
    setFormError(undefined);
    const clean = (o: Record<string, string>) => Object.fromEntries(Object.entries(o).filter(([, v]) => v.trim() !== ""));
    const { confirm: _confirm, ...adminBody } = admin; // eslint-disable-line @typescript-eslint/no-unused-vars
    const res = await postAuth("/api/auth/register", { ...clean(biz), ...clean(adminBody), timezone: Intl.DateTimeFormat().resolvedOptions().timeZone });
    if (res.ok) {
      router.replace("/dashboard");
      router.refresh();
    } else {
      setErrors(res.error.fieldErrors ?? {});
      setFormError(res.error.code === "CONFLICT" ? "An account with this email already exists. If it's yours, enter its current password to add this business to it." : res.error.message);
      if (res.error.fieldErrors && Object.keys(res.error.fieldErrors).some((k) => k in biz)) setStep(1);
      setBusy(false);
    }
  };

  return (
    <div>
      <ol className="mb-6 flex gap-2 text-xs font-medium" aria-label="Sign-up steps">
        {["Your business", "Your admin account"].map((label, i) => (
          <li key={label} aria-current={step === i + 1 ? "step" : undefined} className={`flex-1 rounded-full px-3 py-1.5 text-center ${step === i + 1 ? "bg-brand-600 text-white" : step > i + 1 ? "bg-brand-100 text-brand-700" : "bg-subtle text-muted"}`}>
            {i + 1}. {label}
          </li>
        ))}
      </ol>
      {formError && <Notice tone="danger" className="mb-4">{formError}</Notice>}
      {step === 1 ? (
        <form onSubmit={next} noValidate className="space-y-4">
          <Field label="Business name" required error={errors.businessName}>{(p) => <Input {...p} autoComplete="organization" value={biz.businessName} onChange={(e) => setBiz({ ...biz, businessName: e.target.value })} />}</Field>
          <Field label="Type of business" hint="For example: pharmacy, hardware wholesaler, bookstore. This never limits features." error={errors.businessType}>{(p) => <Input {...p} value={biz.businessType} onChange={(e) => setBiz({ ...biz, businessType: e.target.value })} />}</Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="City" error={errors.city}>{(p) => <Input {...p} value={biz.city} onChange={(e) => setBiz({ ...biz, city: e.target.value })} />}</Field>
            <Field label="Country" error={errors.country}>{(p) => <Input {...p} autoComplete="country-name" value={biz.country} onChange={(e) => setBiz({ ...biz, country: e.target.value })} />}</Field>
          </div>
          <Field label="Business phone" error={errors.businessPhone}>{(p) => <Input {...p} type="tel" value={biz.businessPhone} onChange={(e) => setBiz({ ...biz, businessPhone: e.target.value })} />}</Field>
          <Button type="submit" className="w-full">Continue</Button>
        </form>
      ) : (
        <form onSubmit={submit} noValidate className="space-y-4">
          <Field label="Your full name" required error={errors.fullName}>{(p) => <Input {...p} autoComplete="name" value={admin.fullName} onChange={(e) => setAdmin({ ...admin, fullName: e.target.value })} />}</Field>
          <Field label="Email" required error={errors.email}>{(p) => <Input {...p} type="email" autoComplete="email" value={admin.email} onChange={(e) => setAdmin({ ...admin, email: e.target.value })} />}</Field>
          <Field label="Phone" error={errors.phone}>{(p) => <Input {...p} type="tel" autoComplete="tel" value={admin.phone} onChange={(e) => setAdmin({ ...admin, phone: e.target.value })} />}</Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Password" required hint="At least 8 characters" error={errors.password}>{(p) => <Input {...p} type="password" autoComplete="new-password" value={admin.password} onChange={(e) => setAdmin({ ...admin, password: e.target.value })} />}</Field>
            <Field label="Confirm password" required error={errors.confirm}>{(p) => <Input {...p} type="password" autoComplete="new-password" value={admin.confirm} onChange={(e) => setAdmin({ ...admin, confirm: e.target.value })} />}</Field>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setStep(1)}>Back</Button>
            <Button type="submit" className="flex-1" loading={busy}>Create workspace</Button>
          </div>
          <p className="text-xs text-muted">Your workspace starts with default units and a Main Warehouse. You&apos;ll be its administrator.</p>
        </form>
      )}
    </div>
  );
}

export function JoinForm() {
  const router = useRouter();
  const [code, setCode] = useState("");
  const [preview, setPreview] = useState<InvitationPreview>();
  const [f, setF] = useState({ fullName: "", password: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const check = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!code.trim()) return setErrors({ code: "Enter your invitation code" });
    setBusy(true);
    setErrors({});
    setFormError(undefined);
    try {
      const res = await fetch(`/api/backend/auth/invitations/${encodeURIComponent(code.trim().toUpperCase())}`);
      const body = await res.json().catch(() => null);
      if (res.ok) setPreview(body);
      else setErrors({ code: res.status === 404 ? "That code wasn't found or has expired." : body?.message ?? "Couldn't check the code." });
    } catch {
      setFormError("Couldn't reach the server.");
    } finally {
      setBusy(false);
    }
  };
  const accept = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = acceptInvitationSchema.safeParse({ code: code.trim().toUpperCase(), ...f });
    if (!parsed.success) return setErrors(fieldErrorsOf(parsed.error));
    setBusy(true);
    const res = await postAuth("/api/auth/accept-invitation", { ...parsed.data, fullName: parsed.data.fullName || undefined });
    if (res.ok) {
      router.replace("/dashboard");
      router.refresh();
    } else {
      setErrors(res.error.fieldErrors ?? {});
      setFormError(res.error.message);
      setBusy(false);
    }
  };

  if (!preview) {
    return (
      <form onSubmit={check} noValidate className="space-y-4">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <Field label="Invitation code" error={errors.code} hint="Your business admin shares this code with you.">{(p) => <Input {...p} value={code} onChange={(e) => setCode(e.target.value)} autoCapitalize="characters" className="font-mono uppercase tracking-wider" />}</Field>
        <Button type="submit" className="w-full" loading={busy}>Continue</Button>
      </form>
    );
  }
  return (
    <form onSubmit={accept} noValidate className="space-y-4">
      <Notice tone="info" title={`Join ${preview.businessName}`}>As {ROLE_LABELS[preview.role]} · invitation for {preview.email} · expires {date(preview.expiresAt)}</Notice>
      {formError && <Notice tone="danger">{formError}</Notice>}
      {!preview.existingUser && <Field label="Your full name" error={errors.fullName}>{(p) => <Input {...p} autoComplete="name" value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} />}</Field>}
      <Field label={preview.existingUser ? "Your current password" : "Choose a password"} error={errors.password} hint={preview.existingUser ? "You already have an account; this adds the business to it." : "At least 8 characters"}>
        {(p) => <Input {...p} type="password" autoComplete={preview.existingUser ? "current-password" : "new-password"} value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />}
      </Field>
      <Button type="submit" className="w-full" loading={busy}>Join business</Button>
    </form>
  );
}
