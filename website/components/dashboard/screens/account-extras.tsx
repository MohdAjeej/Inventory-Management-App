"use client";

import { Check, Download, ImagePlus, Monitor, Moon, RotateCcw, Sun, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader, DescriptionList } from "@/components/ui/card";
import { Select } from "@/components/ui/field";
import { ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { fileUrl } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Business, Permission, Role } from "@/lib/api/types";
import { date } from "@/lib/format";
import { ROLE_DEFAULTS, ROLE_LABELS } from "@/lib/permissions";
import { LANGUAGES, usePreferences, type ThemeMode } from "@/lib/preferences";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";

const demoNote = (mode: string) => (mode === "demo" ? " (demo — not saved)" : "");

// ------------------------------------------------------------------ employee detail

export function EmployeeDetailScreen({ id }: { id: number }) {
  const { client, href, me, can } = useDashboard();
  const toast = useToast();
  const emp = useApi(() => api.employeeAdmin.get(client, id), [client, id]);
  const catalog = useApi(() => api.employees.permissions(client), [client]);
  const [draft, setDraft] = useState<Partial<Record<Permission, boolean>> | null>(null);
  const [saving, setSaving] = useState(false);
  if (!can("EMPLOYEE_MANAGE")) return <><PageHeader title="Employee" /><Notice tone="warning">Only business admins can manage employees.</Notice></>;
  if (emp.error) return <Card><ErrorState error={emp.error} onRetry={emp.reload} /></Card>;
  const e = emp.data;
  if (!e) return <Card><LoadingBlock rows={6} /></Card>;
  const overrides = draft ?? e.overrides ?? {};
  const defaults = new Set(catalog.data?.roleDefaults?.[e.role] ?? ROLE_DEFAULTS[e.role]);
  const labels = catalog.data?.permissions?.length ? catalog.data.permissions : (Object.keys(PERMISSION_LABELS) as Permission[]).map((p) => ({ permission: p, label: PERMISSION_LABELS[p] }));
  const isSelf = e.membershipId === me.membershipId;
  const effective = (p: Permission) => (overrides[p] !== undefined ? overrides[p]! : defaults.has(p));

  const setPerm = (p: Permission, on: boolean) => {
    const next = { ...overrides };
    if (on === defaults.has(p)) delete next[p];
    else next[p] = on;
    setDraft(next);
  };
  const save = async (value: Partial<Record<Permission, boolean>>) => {
    setSaving(true);
    try {
      emp.setData(await api.employeeAdmin.setOverrides(client, e.membershipId, value));
      setDraft(null);
      toast(`Permissions saved; they apply immediately${demoNote(client.mode)}`);
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setSaving(false);
    }
  };
  const changeRole = async (role: Role) => {
    try { emp.setData(await api.employees.setRole(client, e.membershipId, role)); setDraft(null); toast(`Role changed to ${ROLE_LABELS[role]}${demoNote(client.mode)}`); } catch (err) { toast((err as Error).message, "error"); }
  };
  const toggleActive = async () => {
    try { emp.setData(e.status === "ACTIVE" ? await api.employees.deactivate(client, e.membershipId) : await api.employees.reactivate(client, e.membershipId)); toast(`${e.fullName} ${e.status === "ACTIVE" ? "deactivated" : "reactivated"}${demoNote(client.mode)}`); } catch (err) { toast((err as Error).message, "error"); }
  };
  const overrideCount = Object.keys(overrides).length;

  return (
    <>
      <PageHeader back={{ href: href("/employees"), label: "Employees" }} title={e.fullName} description={`${e.email}${e.phone ? ` · ${e.phone}` : ""} · joined ${date(e.joinedAt)}`} actions={!isSelf && <Button variant="secondary" onClick={toggleActive}>{e.status === "ACTIVE" ? "Deactivate" : "Reactivate"}</Button>} />
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Permissions"
            description={overrideCount ? `${overrideCount} change${overrideCount === 1 ? "" : "s"} from the ${ROLE_LABELS[e.role]} defaults` : `Using the ${ROLE_LABELS[e.role]} defaults`}
            action={!isSelf && (
              <>
                {overrideCount > 0 && <Button size="sm" variant="ghost" icon={<RotateCcw className="size-4" aria-hidden />} onClick={() => save({})} disabled={saving}>Reset to role defaults</Button>}
                {draft && <Button size="sm" loading={saving} onClick={() => save(draft)}>Save permissions</Button>}
              </>
            )}
          />
          <ul className="divide-y divide-line">
            {labels.map(({ permission, label }) => {
              const on = effective(permission);
              const changed = overrides[permission] !== undefined;
              const locked = isSelf || (e.role === "ADMIN" && permission === "EMPLOYEE_MANAGE");
              return (
                <li key={permission} className="flex items-center justify-between gap-3 px-5 py-2.5">
                  <label htmlFor={`perm-${permission}`} className="min-w-0 text-sm">
                    <span className="font-medium">{label}</span>
                    {changed && <span className="ml-2 rounded-full bg-brand-50 px-2 py-0.5 text-[11px] font-medium text-brand-700">{on ? "Added" : "Removed"}</span>}
                    {locked && e.role === "ADMIN" && permission === "EMPLOYEE_MANAGE" && <span className="block text-xs text-muted">Always on for admins</span>}
                  </label>
                  <input id={`perm-${permission}`} type="checkbox" role="switch" className="size-5 accent-brand-600" checked={on} disabled={locked} onChange={(ev) => setPerm(permission, ev.target.checked)} />
                </li>
              );
            })}
          </ul>
          {isSelf && <CardBody className="border-t border-line text-sm text-muted">You can&apos;t change your own permissions.</CardBody>}
        </Card>
        <Card className="h-fit">
          <CardHeader title="Membership" />
          <CardBody className="space-y-4">
            <DescriptionList className="sm:grid-cols-1" items={[{ label: "Status", value: <StatusChip status={e.status} /> }, { label: "Joined", value: date(e.joinedAt) }]} />
            {!isSelf && (
              <div>
                <label htmlFor="emp-role" className="mb-1.5 block text-sm font-medium text-ink-2">Role</label>
                <Select id="emp-role" value={e.role} onChange={(ev) => changeRole(ev.target.value as Role)}>{(Object.keys(ROLE_LABELS) as Role[]).map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}</Select>
                <p className="mt-1 text-xs text-muted">Changing the role keeps per-person changes on top of the new defaults.</p>
              </div>
            )}
          </CardBody>
        </Card>
      </div>
    </>
  );
}

const PERMISSION_LABELS: Record<Permission, string> = {
  PRODUCT_VIEW: "View products and stock", PRODUCT_CREATE: "Create products", PRODUCT_EDIT: "Edit products", PRODUCT_ARCHIVE: "Archive products",
  STOCK_ADD: "Add stock", STOCK_REDUCE: "Reduce stock", STOCK_ADJUST: "Adjust stock, damage and reservations", STOCK_TRANSFER: "Transfer stock between warehouses",
  STOCK_COUNT: "Run stock counts", REPORT_VIEW: "View reports", REPORT_EXPORT: "Export reports", CATEGORY_MANAGE: "Manage categories, attributes and units",
  WAREHOUSE_MANAGE: "Manage warehouses and locations", EMPLOYEE_MANAGE: "Manage employees and permissions", BUSINESS_SETTINGS: "Edit business profile and settings",
  SALE_RECORD: "Record sales, take payments and manage customers", SALE_CANCEL: "Cancel sales and record returns", ANALYTICS_VIEW: "View sales, profit and product analytics",
  PURCHASE_MANAGE: "Record purchases and manage suppliers",
};

// ------------------------------------------------------------------ logo

export function LogoCard({ business, onChange }: { business: Business; onChange: (b: Business) => void }) {
  const { client, can } = useDashboard();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const upload = async (file: File) => {
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) return toast("Use a JPEG, PNG or WebP image", "error");
    if (file.size > 5 * 1024 * 1024) return toast("The logo must be 5 MB or smaller", "error");
    setBusy(true);
    try { onChange(await client.upload<Business>("/business/logo", file)); toast("Logo updated"); } catch (e) { toast((e as Error).message, "error"); } finally { setBusy(false); if (input.current) input.current.value = ""; }
  };
  return (
    <Card>
      <CardHeader title="Logo" />
      <CardBody className="flex items-center gap-4">
        <div className="grid size-20 shrink-0 place-items-center overflow-hidden rounded-xl border border-line bg-subtle">
          {business.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- served by the backend through the proxy
            <img src={fileUrl(business.logoUrl)} alt={`${business.name} logo`} className="size-full object-contain" />
          ) : <span className="text-2xl font-semibold text-muted">{business.name.charAt(0)}</span>}
        </div>
        {can("BUSINESS_SETTINGS") ? (
          <div>
            <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" id="logo-file" onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
            <Button size="sm" variant="secondary" loading={busy} icon={<ImagePlus className="size-4" aria-hidden />} onClick={() => input.current?.click()}>{business.logoUrl ? "Change logo" : "Upload logo"}</Button>
            <p className="mt-1 text-xs text-muted">JPEG, PNG or WebP, up to 5 MB.</p>
          </div>
        ) : <p className="text-sm text-muted">Only admins can change the logo.</p>}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ backup & restore

export function BackupScreen() {
  const { client, can, me } = useDashboard();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<{ name?: string; products?: number; sales?: number } | null>(null);
  const [error, setError] = useState<string>();
  const [restoring, setRestoring] = useState(false);
  const demo = client.mode === "demo";
  const exportUrl = demo ? null : "/api/backend/backup";

  const choose = async (f: File) => {
    setError(undefined);
    if (f.size > 50 * 1024 * 1024) return setError("Backup files can be at most 50 MB.");
    try {
      const data = JSON.parse(await f.text());
      const count = (k: string) => (Array.isArray(data?.[k]) ? data[k].length : undefined);
      setPreview({ name: data?.business?.name ?? data?.businessName, products: count("products"), sales: count("sales") });
      setFile(f);
    } catch {
      setError("That file isn't a valid backup (it must be the JSON file downloaded from Backup).");
    }
  };
  const restore = async () => {
    if (!file) return;
    setRestoring(true);
    setError(undefined);
    try {
      const res = await fetch("/api/auth/restore-backup", { method: "POST", headers: { "Content-Type": "application/json" }, body: await file.text() });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message ?? "The backup couldn't be restored.");
      }
      // Signed into the new business: reload fully so nothing from the current business stays in memory.
      window.location.assign("/dashboard");
    } catch (e) {
      setError((e as Error).message);
      setRestoring(false);
    }
  };

  return (
    <>
      <PageHeader title="Backup & restore" description="Download a copy of your business data, or create a new business from a backup file." />
      {demo && <Notice tone="warning" className="mb-4">Backup and restore aren&apos;t available in demo mode.</Notice>}
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Download a backup" description={`Everything in ${me.businessName}: catalogue, stock, sales, purchases, customers, suppliers and settings.`} />
          <CardBody className="space-y-3">
            {can("BUSINESS_SETTINGS") ? (
              exportUrl ? <a href={exportUrl} download className="inline-flex h-10 items-center gap-2 rounded-lg bg-brand-600 px-4 text-sm font-medium text-white shadow-sm hover:bg-brand-700"><Download className="size-4" aria-hidden /> Download backup (.json)</a> : <Button disabled icon={<Download className="size-4" aria-hidden />}>Download backup (.json)</Button>
            ) : <Notice>Downloading a backup needs the “Business settings” permission.</Notice>}
            <p className="text-xs text-muted">Shared B2B invoice records and business connections are not included, because they belong to two businesses.</p>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Restore from a backup" description="Restoring creates a NEW business named “… (restored)” and switches you to it. Your current business is not changed." />
          <CardBody className="space-y-3">
            {error && <Notice tone="danger">{error}</Notice>}
            <input ref={input} type="file" accept="application/json,.json" className="sr-only" id="backup-file" onChange={(e) => e.target.files?.[0] && choose(e.target.files[0])} />
            <Button variant="secondary" disabled={demo} icon={<Upload className="size-4" aria-hidden />} onClick={() => input.current?.click()}>Choose backup file</Button>
            {file && preview && (
              <div className="rounded-lg border border-line bg-canvas p-3 text-sm">
                <p className="font-medium">{file.name}</p>
                <p className="text-muted">{[preview.name && `Business: ${preview.name}`, preview.products !== undefined && `${preview.products} products`, preview.sales !== undefined && `${preview.sales} sales`].filter(Boolean).join(" · ") || "Backup file selected"}</p>
              </div>
            )}
            <Button disabled={!file || demo} loading={restoring} onClick={restore}>Restore as a new business</Button>
          </CardBody>
        </Card>
      </div>
    </>
  );
}

// ------------------------------------------------------------------ appearance & language

export function AppearanceScreen() {
  const { theme, setTheme, language, setLanguage } = usePreferences();
  const themes: { value: ThemeMode; label: string; icon: React.ReactNode }[] = [
    { value: "system", label: "System default", icon: <Monitor className="size-5" aria-hidden /> },
    { value: "light", label: "Light", icon: <Sun className="size-5" aria-hidden /> },
    { value: "dark", label: "Dark", icon: <Moon className="size-5" aria-hidden /> },
  ];
  return (
    <>
      <PageHeader title="Appearance & language" description="Saved in this browser." />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Theme" />
          <CardBody>
            <div role="radiogroup" aria-label="Theme" className="grid gap-2 sm:grid-cols-3">
              {themes.map((t) => (
                <button key={t.value} type="button" role="radio" aria-checked={theme === t.value} onClick={() => setTheme(t.value)} className={`flex flex-col items-center gap-2 rounded-lg border p-4 text-sm font-medium transition-colors ${theme === t.value ? "border-brand-600 bg-brand-50 text-brand-700" : "border-line hover:bg-subtle"}`}>
                  {t.icon}
                  {t.label}
                </button>
              ))}
            </div>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Language" description="The same translations as the mobile app. Product names and other data you entered stay as they are." />
          <ul role="radiogroup" aria-label="Language" className="divide-y divide-line">
            {LANGUAGES.map((l) => (
              <li key={l.tag}>
                <button type="button" role="radio" aria-checked={language === l.tag} lang={l.tag} translate="no" onClick={() => setLanguage(l.tag)} className="flex w-full items-center justify-between px-5 py-3 text-left text-sm hover:bg-subtle">
                  <span className={language === l.tag ? "font-semibold text-brand-700" : ""}>{l.name}</span>
                  {language === l.tag && <Check className="size-4 text-brand-700" aria-hidden />}
                </button>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  );
}

