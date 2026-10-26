"use client";

import { Bell, Copy, MailPlus, UserCog } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader, DescriptionList } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Checkbox, Field, Input, Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable, Pagination } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Business, BusinessSettings, Employee, Invitation, Role } from "@/lib/api/types";
import { date, relativeTime } from "@/lib/format";
import { ROLE_DEFAULTS, ROLE_LABELS } from "@/lib/permissions";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";
import { LogoCard } from "./account-extras";

// ------------------------------------------------------------------ employees

export function EmployeesScreen() {
  const { client, me, can, href } = useDashboard();
  const toast = useToast();
  const list = useApi(() => api.employees.list(client, { size: 100 }), [client]);
  const invites = useApi(() => api.employees.invitations(client), [client]);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [created, setCreated] = useState<Invitation>();
  if (!can("EMPLOYEE_MANAGE")) return <><PageHeader title="Employees" /><Notice tone="warning">Only business admins can manage employees.</Notice></>;
  const demo = client.mode === "demo" ? " (demo — not saved)" : "";

  const changeRole = async (e: Employee, role: Role) => {
    try {
      await api.employees.setRole(client, e.membershipId, role);
      toast(`${e.fullName} is now ${ROLE_LABELS[role]}${demo}`);
      list.reload();
    } catch (err) {
      toast((err as Error).message, "error");
    }
  };
  const toggle = async (e: Employee) => {
    try {
      if (e.status === "ACTIVE") await api.employees.deactivate(client, e.membershipId);
      else await api.employees.reactivate(client, e.membershipId);
      toast(`${e.fullName} ${e.status === "ACTIVE" ? "deactivated" : "reactivated"}${demo}`);
      list.reload();
    } catch (err) {
      toast((err as Error).message, "error");
    }
  };

  return (
    <>
      <PageHeader title="Employees" description="Roles set default permissions; changes take effect immediately." actions={<Button icon={<MailPlus className="size-4" aria-hidden />} onClick={() => setInviteOpen(true)}>Invite employee</Button>} />
      <div className="space-y-6">
        {created && (
          <Notice tone="success" title={`Invitation created for ${created.email}`}>
            Share this code: <strong className="font-mono text-base tracking-wider">{created.code}</strong>. They choose “I have an invitation code” when signing in (or use /join on the web). Valid until {date(created.expiresAt)}. No email is sent automatically.
          </Notice>
        )}
        <Card>
          <CardHeader title="Team" />
          {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : !list.data ? <LoadingBlock /> : (
            <DataTable<Employee>
              caption="Employees"
              rows={list.data.content}
              rowKey={(e) => e.membershipId}
              rowHref={(e) => href(`/employees/${e.membershipId}`)}
              columns={[
                { header: "Name", cell: (e) => <span><span className="font-medium">{e.fullName}</span>{e.membershipId === me.membershipId && <span className="text-muted"> (you)</span>}{Object.keys(e.overrides ?? {}).length > 0 && <span className="text-xs text-muted"> · custom permissions</span>}</span> },
                { header: "Email", cell: (e) => e.email, hideOnMobile: true },
                {
                  header: "Role",
                  cell: (e) => e.membershipId === me.membershipId ? ROLE_LABELS[e.role] : (
                    <>
                      <label htmlFor={`role-${e.membershipId}`} className="sr-only">Role for {e.fullName}</label>
                      <Select id={`role-${e.membershipId}`} value={e.role} onChange={(ev) => changeRole(e, ev.target.value as Role)} className="h-8 w-44!">
                        {(Object.keys(ROLE_LABELS) as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                      </Select>
                    </>
                  ),
                },
                { header: "Status", cell: (e) => <StatusChip status={e.status} /> },
                { header: "Joined", cell: (e) => date(e.joinedAt), hideOnMobile: true },
                { header: "Actions", align: "right", cell: (e) => e.membershipId === me.membershipId ? null : <Button size="sm" variant="ghost" onClick={() => toggle(e)}>{e.status === "ACTIVE" ? "Deactivate" : "Reactivate"}</Button> },
              ]}
            />
          )}
        </Card>
        <Card>
          <CardHeader title="Pending invitations" />
          {invites.error ? <ErrorState error={invites.error} onRetry={invites.reload} /> : !invites.data ? <LoadingBlock rows={2} /> : invites.data.content.length === 0 ? <EmptyState icon={MailPlus} title="No pending invitations" /> : (
            <ul className="divide-y divide-line">
              {invites.data.content.map((i) => (
                <li key={i.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 text-sm">
                  <div>
                    <p className="font-medium">{i.email} · {ROLE_LABELS[i.role]}</p>
                    <p className="text-xs text-muted">Code <span className="font-mono">{i.code}</span> · expires {date(i.expiresAt)}</p>
                  </div>
                  <div className="flex gap-2">
                    <Button size="sm" variant="secondary" icon={<Copy className="size-3.5" aria-hidden />} onClick={() => { navigator.clipboard?.writeText(i.code); toast("Code copied"); }}>Copy code</Button>
                    <Button size="sm" variant="ghost" onClick={async () => { try { await api.employees.cancelInvitation(client, i.id); invites.reload(); toast("Invitation cancelled" + demo); } catch (err) { toast((err as Error).message, "error"); } }}>Cancel</Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <CardHeader title="Role defaults" description="Open an employee to add or remove individual permissions on top of their role." />
          <CardBody className="grid gap-4 md:grid-cols-3">
            {(Object.keys(ROLE_DEFAULTS) as Role[]).map((r) => (
              <div key={r}>
                <p className="mb-1 flex items-center gap-1.5 text-sm font-semibold"><UserCog className="size-4 text-brand-600" aria-hidden /> {ROLE_LABELS[r]}</p>
                <p className="text-xs text-muted">{ROLE_DEFAULTS[r].length} permissions: {ROLE_DEFAULTS[r].map((p) => p.toLowerCase().replaceAll("_", " ")).join(", ")}</p>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>
      <InviteDialog open={inviteOpen} onClose={() => setInviteOpen(false)} onCreated={(i) => { setCreated(i); invites.reload(); }} />
    </>
  );
}

function InviteDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (i: Invitation) => void }) {
  const { client } = useDashboard();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("STAFF");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (open) { setEmail(""); setErrors({}); } }, [open]);
  const submit = async () => {
    if (!/^\S+@\S+\.\S+$/.test(email)) return setErrors({ email: "Enter a valid email address" });
    setSaving(true);
    try {
      onCreated(await api.employees.invite(client, { email, role }));
      onClose();
    } catch (e) {
      setErrors(e instanceof ApiError ? { email: e.fieldErrors.email ?? e.message } : { email: "Something went wrong" });
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={open} onClose={onClose} title="Invite an employee" description="You'll get a code to share. Invitations are valid for 7 days." footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={saving}>Create invitation</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Email" required error={errors.email}>{(p) => <Input {...p} type="email" value={email} onChange={(e) => setEmail(e.target.value)} />}</Field>
        <Field label="Role">{(p) => <Select {...p} value={role} onChange={(e) => setRole(e.target.value as Role)}>{(Object.keys(ROLE_LABELS) as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}</Select>}</Field>
      </div>
    </Dialog>
  );
}

// ------------------------------------------------------------------ business settings

export function SettingsScreen() {
  const { client, can, href } = useDashboard();
  const toast = useToast();
  const editable = can("BUSINESS_SETTINGS");
  const business = useApi(() => api.business.get(client), [client]);
  const settings = useApi(() => api.business.settings(client), [client]);
  const network = useApi(() => api.network.me(client), [client]);
  const [b, setB] = useState<Business>();
  const [s, setS] = useState<BusinessSettings>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<string>();
  useEffect(() => { if (business.data) setB(business.data); }, [business.data]);
  useEffect(() => { if (settings.data) setS(settings.data); }, [settings.data]);
  const demo = client.mode === "demo" ? " (demo — not saved)" : "";

  const saveBusiness = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!b) return;
    if (!b.name?.trim()) return setErrors({ name: "Business name is required" });
    setSaving("business");
    setErrors({});
    try {
      const { name, businessType, email, phone, address, city, state, country, postalCode, taxId } = b;
      business.setData(await api.business.update(client, { name, businessType, email, phone, address, city, state, country, postalCode, taxId }));
      toast("Business profile saved" + demo);
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldErrors);
      toast((err as Error).message, "error");
    } finally {
      setSaving(undefined);
    }
  };
  const saveSettings = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!s) return;
    setSaving("settings");
    try {
      settings.setData(await api.business.updateSettings(client, s));
      toast("Settings saved" + demo);
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setSaving(undefined);
    }
  };

  if (business.error) return <Card><ErrorState error={business.error} onRetry={business.reload} /></Card>;
  const bf = (k: keyof Business) => ({ value: (b?.[k] as string) ?? "", onChange: (e: { target: { value: string } }) => setB((x) => (x ? { ...x, [k]: e.target.value } : x)), disabled: !editable });

  return (
    <>
      <PageHeader title="Business settings" description={editable ? "Your business profile and inventory rules." : "Only business admins can change these settings."} />
      <div className="grid gap-6 lg:grid-cols-3">
        <form onSubmit={saveBusiness} noValidate className="lg:col-span-2">
          <Card>
            <CardHeader title="Business profile" />
            {!b ? <LoadingBlock /> : (
              <CardBody className="grid gap-4 sm:grid-cols-2">
                <Field label="Business name" required error={errors.name}>{(p) => <Input {...p} {...bf("name")} maxLength={200} />}</Field>
                <Field label="Business type" hint="A description only; it never limits features.">{(p) => <Input {...p} {...bf("businessType")} maxLength={120} />}</Field>
                <Field label="Email" error={errors.email}>{(p) => <Input {...p} type="email" {...bf("email")} />}</Field>
                <Field label="Phone">{(p) => <Input {...p} type="tel" {...bf("phone")} />}</Field>
                <Field label="Address" className="sm:col-span-2">{(p) => <Input {...p} {...bf("address")} />}</Field>
                <Field label="City">{(p) => <Input {...p} {...bf("city")} />}</Field>
                <Field label="State">{(p) => <Input {...p} {...bf("state")} />}</Field>
                <Field label="Country">{(p) => <Input {...p} {...bf("country")} />}</Field>
                <Field label="Postal code">{(p) => <Input {...p} {...bf("postalCode")} />}</Field>
                <Field label="Tax registration (e.g. GSTIN)">{(p) => <Input {...p} {...bf("taxId")} />}</Field>
                {editable && <div className="flex items-end justify-end sm:col-span-2"><Button type="submit" loading={saving === "business"}>Save profile</Button></div>}
              </CardBody>
            )}
          </Card>
        </form>
        <div className="space-y-6">
          {business.data && <LogoCard business={business.data} onChange={(b) => business.setData(b)} />}
          <Card>
            <CardHeader title="Business network" />
            <CardBody>
              {network.data ? <DescriptionList className="sm:grid-cols-1" items={[{ label: "Your business code", value: <span className="font-mono text-base tracking-wider">{network.data.businessCode}</span> }, { label: "Visible in search", value: network.data.discoverable ? "Yes" : "No" }]} /> : <LoadingBlock rows={2} />}
              <p className="mt-3 text-xs text-muted">Share the code so other businesses can connect with you. Manage connections on the <Link href={href("/network")} className="font-medium text-brand-700 hover:underline">Business network</Link> screen.</p>
            </CardBody>
          </Card>
        </div>
        <form onSubmit={saveSettings} noValidate className="lg:col-span-2">
          <Card>
            <CardHeader title="Inventory rules" />
            {!s ? <LoadingBlock /> : (
              <CardBody className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-3">
                  <Field label="Default low-stock threshold">{(p) => <Input {...p} type="number" min={0} value={s.defaultLowStockThreshold} disabled={!editable} onChange={(e) => setS({ ...s, defaultLowStockThreshold: Number(e.target.value) })} />}</Field>
                  <Field label="Currency" hint="3-letter code, e.g. INR">{(p) => <Input {...p} value={s.currencyCode ?? ""} maxLength={3} disabled={!editable} onChange={(e) => setS({ ...s, currencyCode: e.target.value.toUpperCase() })} />}</Field>
                  <Field label="Timezone">{(p) => <Input {...p} value={s.timezone ?? ""} disabled={!editable} onChange={(e) => setS({ ...s, timezone: e.target.value })} />}</Field>
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Checkbox label="Low-stock alerts" description="Notify when stock falls to the minimum" checked={s.notifyLowStock} disabled={!editable} onChange={(e) => setS({ ...s, notifyLowStock: e.target.checked })} />
                  <Checkbox label="Allow negative stock" description="Off: sales are refused when stock is short" checked={s.allowNegativeStock} disabled={!editable} onChange={(e) => setS({ ...s, allowNegativeStock: e.target.checked })} />
                  <Checkbox label="Track batches / lots" checked={s.trackBatches} disabled={!editable} onChange={(e) => setS({ ...s, trackBatches: e.target.checked })} />
                  <Checkbox label="Track expiry dates" checked={s.trackExpiry} disabled={!editable} onChange={(e) => setS({ ...s, trackExpiry: e.target.checked })} />
                  <Checkbox label="Track serial numbers" checked={s.trackSerials} disabled={!editable} onChange={(e) => setS({ ...s, trackSerials: e.target.checked })} />
                  <Checkbox label="Track damaged and reserved stock" checked={s.trackDamagedReserved} disabled={!editable} onChange={(e) => setS({ ...s, trackDamagedReserved: e.target.checked })} />
                </div>
                {editable && <div className="flex justify-end"><Button type="submit" loading={saving === "settings"}>Save settings</Button></div>}
              </CardBody>
            )}
          </Card>
        </form>
      </div>
    </>
  );
}

// ------------------------------------------------------------------ profile

export function ProfileScreen() {
  const { client, me, setMe } = useDashboard();
  const toast = useToast();
  const [fullName, setFullName] = useState(me.fullName);
  const [phone, setPhone] = useState(me.phone ?? "");
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<string>();

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim()) return setErrors({ fullName: "Your name is required" });
    setSaving("profile");
    try {
      setMe(await api.updateMe(client, { fullName: fullName.trim(), phone: phone || undefined }));
      toast("Profile saved" + (client.mode === "demo" ? " (demo — not saved)" : ""));
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setSaving(undefined);
    }
  };
  const changePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!pw.current) errs.current = "Enter your current password";
    if (pw.next.length < 8) errs.next = "Password must be at least 8 characters";
    if (pw.next !== pw.confirm) errs.confirm = "Passwords don't match";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving("password");
    try {
      await api.changePassword(client, { currentPassword: pw.current, newPassword: pw.next });
      // The backend revokes every session after a password change.
      await fetch("/api/auth/logout", { method: "POST" });
      window.location.assign("/login?changed=1");
    } catch (err) {
      setErrors({ current: (err as Error).message });
      setSaving(undefined);
    }
  };

  return (
    <>
      <PageHeader title="Profile" description={`${me.email} · ${ROLE_LABELS[me.role]} at ${me.businessName}`} />
      <div className="grid gap-6 lg:grid-cols-2">
        <form onSubmit={saveProfile} noValidate>
          <Card>
            <CardHeader title="Your details" />
            <CardBody className="space-y-4">
              <Field label="Full name" required error={errors.fullName}>{(p) => <Input {...p} value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={200} />}</Field>
              <Field label="Phone">{(p) => <Input {...p} type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} maxLength={50} />}</Field>
              <Field label="Email" hint="Email can't be changed here.">{(p) => <Input {...p} value={me.email} disabled />}</Field>
              <div className="flex justify-end"><Button type="submit" loading={saving === "profile"}>Save</Button></div>
            </CardBody>
          </Card>
        </form>
        <form onSubmit={changePassword} noValidate>
          <Card>
            <CardHeader title="Change password" description="You'll be signed out on all devices." />
            <CardBody className="space-y-4">
              <Field label="Current password" error={errors.current}>{(p) => <Input {...p} type="password" autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />}</Field>
              <Field label="New password" error={errors.next} hint="At least 8 characters">{(p) => <Input {...p} type="password" autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} />}</Field>
              <Field label="Confirm new password" error={errors.confirm}>{(p) => <Input {...p} type="password" autoComplete="new-password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} />}</Field>
              <div className="flex justify-end"><Button type="submit" loading={saving === "password"}>Change password</Button></div>
            </CardBody>
          </Card>
        </form>
        {me.memberships.length > 1 && (
          <Card className="lg:col-span-2">
            <CardHeader title="Your businesses" description="Switch from the business menu in the top bar. Data from different businesses is never mixed." />
            <ul className="divide-y divide-line">{me.memberships.map((m) => <li key={m.businessId} className="flex justify-between px-5 py-3 text-sm"><span>{m.businessName}</span><span className="text-muted">{ROLE_LABELS[m.role]}</span></li>)}</ul>
          </Card>
        )}
      </div>
    </>
  );
}

// ------------------------------------------------------------------ notifications

export function NotificationsScreen() {
  const { client, href } = useDashboard();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const list = useApi(() => api.notifications.list(client, { page, size: 20 }), [client, page]);
  const link = (payload?: Record<string, unknown>) =>
    payload?.tradeInvoiceId ? href(`/invoices/${payload.tradeInvoiceId}`) : payload?.productId ? href(`/products/${payload.productId}`) : undefined;
  return (
    <>
      <PageHeader title="Notifications" description="Stock alerts, team changes and business network activity." actions={<Button variant="secondary" onClick={async () => { await api.notifications.markAllRead(client); list.reload(); toast("All marked as read"); }}>Mark all as read</Button>} />
      <Card>
        {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : !list.data ? <LoadingBlock /> : list.data.content.length === 0 ? <EmptyState icon={Bell} title="You're all caught up" /> : (
          <>
            <ul className="divide-y divide-line">
              {list.data.content.map((n) => {
                const to = link(n.payload);
                return (
                  <li key={n.id} className={`flex items-start gap-3 px-5 py-3 ${n.read ? "" : "bg-brand-50/50"}`}>
                    <span className={`mt-1.5 size-2 shrink-0 rounded-full ${n.read ? "bg-transparent" : "bg-brand-600"}`} aria-hidden />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium">{n.read ? "" : <span className="sr-only">Unread: </span>}{n.title}</p>
                      {n.body && <p className="text-sm text-muted">{n.body}</p>}
                      <p className="mt-0.5 text-xs text-muted">{relativeTime(n.createdAt)}</p>
                    </div>
                    {to && <Link href={to} onClick={() => !n.read && api.notifications.markRead(client, n.id)} className="shrink-0 text-sm font-medium text-brand-700 hover:underline">Open</Link>}
                  </li>
                );
              })}
            </ul>
            <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  );
}
