"use client";

import { Archive, ArchiveRestore, ArrowRightLeft, ChevronRight, Pencil, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Attribute, AttributeRequest, Category, FieldType, Unit } from "@/lib/api/types";
import { humanize, qty } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";
import { flattenCategories } from "./products";

const demoNote = (mode: string) => (mode === "demo" ? " (demo — not saved)" : "");
type Tab = "categories" | "fields" | "units" | "conversions";

export function CatalogScreen() {
  const { can } = useDashboard();
  const [tab, setTab] = useState<Tab>("categories");
  const manage = can("CATEGORY_MANAGE");
  return (
    <>
      <PageHeader title="Categories & fields" description="Organise your catalogue: categories and subcategories, custom product fields, units and unit conversions." />
      {!manage && <Notice className="mb-4">You can view the setup. Changing it needs the “Manage categories, attributes and units” permission.</Notice>}
      <Card>
        <div className="px-4 pt-2">
          <Tabs label="Catalog sections" value={tab} onChange={setTab} tabs={[{ key: "categories", label: "Categories" }, { key: "fields", label: "Custom fields" }, { key: "units", label: "Units" }, { key: "conversions", label: "Unit conversions" }]} />
        </div>
        {tab === "categories" && <CategoriesTab manage={manage} />}
        {tab === "fields" && <FieldsTab manage={manage} />}
        {tab === "units" && <UnitsTab manage={manage} />}
        {tab === "conversions" && <ConversionsTab manage={manage} />}
      </Card>
    </>
  );
}

// ------------------------------------------------------------------ categories

function CategoriesTab({ manage }: { manage: boolean }) {
  const { client } = useDashboard();
  const toast = useToast();
  const [showArchived, setShowArchived] = useState(false);
  const cats = useApi(() => api.catalog.categories(client, showArchived), [client, showArchived]);
  const [editing, setEditing] = useState<{ category?: Category; parentId?: number } | null>(null);
  const toggle = async (c: Category) => {
    try {
      await api.catalogAdmin.setCategoryArchived(client, c.id, !c.archived);
      toast(`${c.archived ? "Restored" : "Archived"} ${c.name}${demoNote(client.mode)}`);
      cats.reload();
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };
  const render = (nodes: Category[]): React.ReactNode =>
    nodes.map((c) => (
      <li key={c.id} className="list-none">
        <div className="flex items-center justify-between gap-3 rounded-lg px-2 py-2 hover:bg-canvas" style={{ paddingLeft: `${c.depth * 1.25 + 0.5}rem` }}>
          <span className="flex min-w-0 items-center gap-1.5 text-sm">
            {c.depth > 0 && <ChevronRight className="size-3.5 shrink-0 text-muted" aria-hidden />}
            <span className="truncate font-medium">{c.name}</span>
            {c.archived && <StatusChip status="ARCHIVED" />}
            <span className="shrink-0 text-xs text-muted tabular">· {c.productCount} products</span>
          </span>
          {manage && (
            <span className="flex shrink-0 gap-1">
              {!c.archived && <button type="button" className="rounded-md px-2 py-1 text-xs font-medium text-brand-700 hover:bg-brand-50" onClick={() => setEditing({ parentId: c.id })}>+ Subcategory</button>}
              <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={`Edit ${c.name}`} onClick={() => setEditing({ category: c })}><Pencil className="size-4" aria-hidden /></button>
              <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={c.archived ? `Restore ${c.name}` : `Archive ${c.name}`} onClick={() => toggle(c)}>
                {c.archived ? <ArchiveRestore className="size-4" aria-hidden /> : <Archive className="size-4" aria-hidden />}
              </button>
            </span>
          )}
        </div>
        {c.children?.length > 0 && <ul>{render(c.children)}</ul>}
      </li>
    ));
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-4">
        <Checkbox label="Show archived" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
        {manage && <Button size="sm" icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing({})}>Add category</Button>}
      </div>
      {cats.error ? <ErrorState error={cats.error} onRetry={cats.reload} /> : !cats.data ? <LoadingBlock /> : cats.data.length === 0 ? <EmptyState title="No categories yet" description="Create categories such as Medicines › Tablets or Books › Fiction." /> : <ul className="p-3">{render(cats.data)}</ul>}
      <CategoryDialog state={editing} tree={cats.data ?? []} onClose={() => setEditing(null)} onSaved={cats.reload} />
    </>
  );
}

function CategoryDialog({ state, tree, onClose, onSaved }: { state: { category?: Category; parentId?: number } | null; tree: Category[]; onClose: () => void; onSaved: () => void }) {
  const { client } = useDashboard();
  const toast = useToast();
  const flat = useMemo(() => flattenCategories(tree), [tree]);
  const [f, setF] = useState({ name: "", description: "", parentId: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [last, setLast] = useState<typeof state>(null);
  if (state !== last) {
    setLast(state);
    setErrors({});
    setF({ name: state?.category?.name ?? "", description: state?.category?.description ?? "", parentId: String(state?.category?.parentId ?? state?.parentId ?? "") });
  }
  const c = state?.category;
  const submit = async () => {
    if (!f.name.trim()) return setErrors({ name: "Name is required" });
    setSaving(true);
    const body = { name: f.name.trim(), description: f.description || undefined, parentId: f.parentId ? Number(f.parentId) : null };
    try {
      if (c) await api.catalogAdmin.updateCategory(client, c.id, body);
      else await api.catalogAdmin.createCategory(client, body);
      toast(`${c ? "Category updated" : "Category added"}${demoNote(client.mode)}`);
      onSaved();
      onClose();
    } catch (e) {
      setErrors(e instanceof ApiError ? { name: e.fieldErrors.name ?? e.message } : { name: "Something went wrong" });
    } finally {
      setSaving(false);
    }
  };
  // A category can't be moved under itself or its own subcategories.
  const blocked = new Set<number>();
  if (c) flattenCategories([flat.find((x) => x.id === c.id) ?? c]).forEach((x) => blocked.add(x.id));
  return (
    <Dialog open={state !== null} onClose={onClose} title={c ? `Edit ${c.name}` : "Add category"} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={saving}>Save</Button></>}>
      <div className="space-y-4">
        <Field label="Name" required error={errors.name}>{(p) => <Input {...p} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} maxLength={120} />}</Field>
        <Field label="Parent category" hint="Subcategories inherit their parents' custom fields.">
          {(p) => <Select {...p} value={f.parentId} onChange={(e) => setF({ ...f, parentId: e.target.value })}><option value="">None (top level)</option>{flat.filter((x) => !blocked.has(x.id) && !x.archived).map((x) => <option key={x.id} value={x.id}>{x.path}</option>)}</Select>}
        </Field>
        <Field label="Description">{(p) => <Textarea {...p} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} maxLength={1000} className="min-h-16" />}</Field>
      </div>
    </Dialog>
  );
}

// ------------------------------------------------------------------ custom fields

const FIELD_TYPES: FieldType[] = ["TEXT", "NUMBER", "DATE", "BOOLEAN", "DROPDOWN", "MULTI_SELECT"];

function FieldsTab({ manage }: { manage: boolean }) {
  const { client } = useDashboard();
  const toast = useToast();
  const attrs = useApi(() => api.catalogAdmin.attributes(client, true), [client]);
  const cats = useApi(() => api.catalog.categories(client), [client]);
  const [editing, setEditing] = useState<Attribute | "new" | null>(null);
  const toggle = async (a: Attribute) => {
    try {
      await api.catalogAdmin.setAttributeArchived(client, a.id, !a.archived);
      toast(`${a.archived ? "Restored" : "Archived"} ${a.label}${demoNote(client.mode)}`);
      attrs.reload();
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-4">
        <p className="text-sm text-muted">Fields for a category also apply to its subcategories. Fields without a category apply to all products.</p>
        {manage && <Button size="sm" icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing("new")}>Add field</Button>}
      </div>
      {attrs.error ? <ErrorState error={attrs.error} onRetry={attrs.reload} /> : !attrs.data ? <LoadingBlock /> : attrs.data.length === 0 ? <EmptyState title="No custom fields yet" description="For example Author for books, or Strength and Dosage form for medicines." /> : (
        <DataTable
          caption="Custom fields"
          rows={attrs.data}
          rowKey={(a) => a.id}
          columns={[
            { header: "Field", cell: (a) => <span className="font-medium">{a.label} <span className="font-mono text-xs text-muted">{a.key}</span></span> },
            { header: "Type", cell: (a) => humanize(a.fieldType) },
            { header: "Applies to", cell: (a) => a.categoryName ?? "All products" },
            { header: "Required", cell: (a) => (a.required ? "Yes" : "No") },
            { header: "Options", cell: (a) => a.options?.join(", ") || "—", hideOnMobile: true },
            { header: "Status", cell: (a) => <StatusChip status={a.archived ? "ARCHIVED" : "ACTIVE"} /> },
            ...(manage ? [{ header: "Actions", align: "right" as const, cell: (a: Attribute) => (
              <div className="flex justify-end gap-1">
                <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={`Edit ${a.label}`} onClick={() => setEditing(a)}><Pencil className="size-4" aria-hidden /></button>
                <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={a.archived ? `Restore ${a.label}` : `Archive ${a.label}`} onClick={() => toggle(a)}>{a.archived ? <ArchiveRestore className="size-4" aria-hidden /> : <Archive className="size-4" aria-hidden />}</button>
              </div>
            ) }] : []),
          ]}
        />
      )}
      <FieldDialog field={editing} categories={flattenCategories(cats.data ?? [])} onClose={() => setEditing(null)} onSaved={attrs.reload} />
    </>
  );
}

function FieldDialog({ field, categories, onClose, onSaved }: { field: Attribute | "new" | null; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  const { client } = useDashboard();
  const toast = useToast();
  const existing = field && field !== "new" ? field : null;
  const [f, setF] = useState<{ key: string; label: string; fieldType: FieldType; required: boolean; options: string; sortOrder: string; categoryId: string }>({ key: "", label: "", fieldType: "TEXT", required: false, options: "", sortOrder: "", categoryId: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [last, setLast] = useState<typeof field>(null);
  if (field !== last) {
    setLast(field);
    setErrors({});
    setFormError(undefined);
    setF({ key: existing?.key ?? "", label: existing?.label ?? "", fieldType: existing?.fieldType ?? "TEXT", required: existing?.required ?? false, options: existing?.options?.join("\n") ?? "", sortOrder: existing?.sortOrder !== undefined ? String(existing.sortOrder) : "", categoryId: String(existing?.categoryId ?? "") });
  }
  const needsOptions = f.fieldType === "DROPDOWN" || f.fieldType === "MULTI_SELECT";
  const submit = async () => {
    const errs: Record<string, string> = {};
    if (!f.label.trim()) errs.label = "Label is required";
    if (!/^[a-z][a-z0-9_]{0,59}$/.test(f.key)) errs.key = "Use lowercase letters, digits and underscores, starting with a letter";
    const options = f.options.split("\n").map((o) => o.trim()).filter(Boolean);
    if (needsOptions && options.length === 0) errs.options = "Add at least one option";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const body: AttributeRequest = { key: f.key, label: f.label.trim(), fieldType: f.fieldType, required: f.required, options: needsOptions ? options : [], sortOrder: f.sortOrder ? Number(f.sortOrder) : undefined, categoryId: f.categoryId ? Number(f.categoryId) : null };
    setSaving(true);
    try {
      if (existing) await api.catalogAdmin.updateAttribute(client, existing.id, body);
      else await api.catalogAdmin.createAttribute(client, body);
      toast(`${existing ? "Field updated" : "Field added"}${demoNote(client.mode)}`);
      onSaved();
      onClose();
    } catch (e) {
      if (e instanceof ApiError) { setErrors(e.fieldErrors); setFormError(e.message); }
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={field !== null} onClose={onClose} title={existing ? `Edit ${existing.label}` : "Add custom field"} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={saving}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {formError && <Notice tone="danger" className="sm:col-span-2">{formError}</Notice>}
        <Field label="Label" required error={errors.label}>{(p) => <Input {...p} value={f.label} onChange={(e) => setF({ ...f, label: e.target.value, key: existing || f.key ? f.key : e.target.value.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").replace(/^(\d)/, "f_$1").slice(0, 60) })} maxLength={120} />}</Field>
        <Field label="Key" required error={errors.key} hint="Stored with each product; e.g. author">{(p) => <Input {...p} className="font-mono" value={f.key} onChange={(e) => setF({ ...f, key: e.target.value })} maxLength={60} />}</Field>
        <Field label="Type">{(p) => <Select {...p} value={f.fieldType} onChange={(e) => setF({ ...f, fieldType: e.target.value as FieldType })}>{FIELD_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}</Select>}</Field>
        <Field label="Applies to">{(p) => <Select {...p} value={f.categoryId} onChange={(e) => setF({ ...f, categoryId: e.target.value })}><option value="">All products</option>{categories.filter((c) => !c.archived).map((c) => <option key={c.id} value={c.id}>{c.path}</option>)}</Select>}</Field>
        {needsOptions && <Field label="Options" required error={errors.options} hint="One per line" className="sm:col-span-2">{(p) => <Textarea {...p} value={f.options} onChange={(e) => setF({ ...f, options: e.target.value })} />}</Field>}
        <Field label="Sort order">{(p) => <Input {...p} type="number" value={f.sortOrder} onChange={(e) => setF({ ...f, sortOrder: e.target.value })} />}</Field>
        <div className="flex items-end"><Checkbox label="Required" checked={f.required} onChange={(e) => setF({ ...f, required: e.target.checked })} /></div>
      </div>
    </Dialog>
  );
}

// ------------------------------------------------------------------ units

function UnitsTab({ manage }: { manage: boolean }) {
  const { client } = useDashboard();
  const toast = useToast();
  const units = useApi(() => api.catalogAdmin.units(client), [client]);
  const [editing, setEditing] = useState<Unit | "new" | null>(null);
  const [f, setF] = useState({ code: "", name: "", decimals: "0" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const open = (u: Unit | "new") => {
    setEditing(u);
    setErrors({});
    setF(u === "new" ? { code: "", name: "", decimals: "0" } : { code: u.code, name: u.name, decimals: String(u.decimals) });
  };
  const submit = async () => {
    const errs: Record<string, string> = {};
    if (!f.code.trim()) errs.code = "Code is required";
    if (!f.name.trim()) errs.name = "Name is required";
    const d = Number(f.decimals);
    if (!(d >= 0 && d <= 4)) errs.decimals = "Between 0 and 4";
    setErrors(errs);
    if (Object.keys(errs).length || !editing) return;
    setSaving(true);
    try {
      const body = { code: f.code.trim(), name: f.name.trim(), decimals: d };
      if (editing === "new") await api.catalogAdmin.createUnit(client, body);
      else await api.catalogAdmin.updateUnit(client, editing.id, body);
      toast(`Unit saved${demoNote(client.mode)}`);
      units.reload();
      setEditing(null);
    } catch (e) {
      setErrors(e instanceof ApiError ? { ...e.fieldErrors, code: e.fieldErrors.code ?? e.message } : {});
    } finally {
      setSaving(false);
    }
  };
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-4">
        <p className="text-sm text-muted">Decimal places decide how precisely quantities can be entered (e.g. kg with 3).</p>
        {manage && <Button size="sm" icon={<Plus className="size-4" aria-hidden />} onClick={() => open("new")}>Add unit</Button>}
      </div>
      {units.error ? <ErrorState error={units.error} onRetry={units.reload} /> : !units.data ? <LoadingBlock /> : (
        <DataTable caption="Units" rows={units.data} rowKey={(u) => u.id} columns={[
          { header: "Unit", cell: (u) => u.name },
          { header: "Code", cell: (u) => <span className="font-mono">{u.code}</span> },
          { header: "Decimal places", align: "right", cell: (u) => u.decimals },
          { header: "Status", cell: (u) => <StatusChip status={u.archived ? "ARCHIVED" : "ACTIVE"} /> },
          ...(manage ? [{ header: "Actions", align: "right" as const, cell: (u: Unit) => (
            <div className="flex justify-end gap-1">
              <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={`Edit ${u.name}`} onClick={() => open(u)}><Pencil className="size-4" aria-hidden /></button>
              <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={u.archived ? `Restore ${u.name}` : `Archive ${u.name}`} onClick={async () => { try { await api.catalogAdmin.setUnitArchived(client, u.id, !u.archived); units.reload(); } catch (e) { toast((e as Error).message, "error"); } }}>{u.archived ? <ArchiveRestore className="size-4" aria-hidden /> : <Archive className="size-4" aria-hidden />}</button>
            </div>
          ) }] : []),
        ]} />
      )}
      <Dialog open={editing !== null} onClose={() => setEditing(null)} title={editing === "new" ? "Add unit" : "Edit unit"} footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button><Button onClick={submit} loading={saving}>Save</Button></>}>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Code" required error={errors.code}>{(p) => <Input {...p} value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} maxLength={20} placeholder="e.g. sht" />}</Field>
          <Field label="Name" required error={errors.name}>{(p) => <Input {...p} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} maxLength={80} placeholder="e.g. Sheets" />}</Field>
          <Field label="Decimal places" error={errors.decimals}>{(p) => <Input {...p} type="number" min={0} max={4} value={f.decimals} onChange={(e) => setF({ ...f, decimals: e.target.value })} />}</Field>
        </div>
      </Dialog>
    </>
  );
}

// ------------------------------------------------------------------ conversions

function ConversionsTab({ manage }: { manage: boolean }) {
  const { client } = useDashboard();
  const toast = useToast();
  const units = useApi(() => api.catalog.units(client), [client]);
  const conv = useApi(() => api.catalogAdmin.conversions(client), [client]);
  const [f, setF] = useState({ fromUnitId: "", toUnitId: "", factor: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [calc, setCalc] = useState({ quantity: "1", fromUnitId: "", toUnitId: "" });
  const [result, setResult] = useState<string>();
  const active = (units.data ?? []).filter((u) => !u.archived);

  const add = async () => {
    const errs: Record<string, string> = {};
    if (!f.fromUnitId) errs.fromUnitId = "Choose a unit";
    if (!f.toUnitId) errs.toUnitId = "Choose a unit";
    if (f.fromUnitId && f.fromUnitId === f.toUnitId) errs.toUnitId = "Choose a different unit";
    if (!(Number(f.factor) > 0)) errs.factor = "Factor must be positive";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    try {
      await api.catalogAdmin.createConversion(client, { fromUnitId: Number(f.fromUnitId), toUnitId: Number(f.toUnitId), factor: Number(f.factor) });
      toast(`Conversion added${demoNote(client.mode)}`);
      setF({ fromUnitId: "", toUnitId: "", factor: "" });
      conv.reload();
    } catch (e) {
      setErrors({ factor: (e as Error).message });
    }
  };
  const convert = async () => {
    try {
      const r = await api.catalogAdmin.convert(client, { quantity: calc.quantity, fromUnitId: calc.fromUnitId, toUnitId: calc.toUnitId });
      setResult(`${qty(r.quantity)} ${r.fromUnit} = ${qty(r.result, 6)} ${r.toUnit}`);
    } catch (e) {
      setResult((e as Error).message);
    }
  };
  const unitSelect = (value: string, onChange: (v: string) => void, label: string, error?: string) => (
    <Field label={label} error={error}>{(p) => <Select {...p} value={value} onChange={(e) => onChange(e.target.value)}><option value="">Choose…</option>{active.map((u) => <option key={u.id} value={u.id}>{u.name} ({u.code})</option>)}</Select>}</Field>
  );
  return (
    <div className="space-y-4 p-4">
      <p className="text-sm text-muted">Example: 1 box = 12 pieces. Conversions are never applied automatically between businesses on B2B invoices.</p>
      {conv.error ? <ErrorState error={conv.error} onRetry={conv.reload} /> : !conv.data ? <LoadingBlock rows={2} /> : conv.data.length === 0 ? <EmptyState icon={ArrowRightLeft} title="No unit conversions yet" /> : (
        <ul className="divide-y divide-line rounded-lg border border-line">
          {conv.data.map((c) => (
            <li key={c.id} className="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
              <span>1 {c.fromUnit.name} = <strong className="tabular">{qty(c.factor, 6)}</strong> {c.toUnit.name}{c.productName && <span className="text-muted"> · only for {c.productName}</span>}</span>
              {manage && <button type="button" className="rounded-md p-1.5 text-muted hover:bg-danger-bg hover:text-danger" aria-label="Delete conversion" onClick={async () => { try { await api.catalogAdmin.deleteConversion(client, c.id); conv.reload(); } catch (e) { toast((e as Error).message, "error"); } }}><Trash2 className="size-4" aria-hidden /></button>}
            </li>
          ))}
        </ul>
      )}
      {manage && (
        <Card>
          <CardHeader title="Add conversion" />
          <CardBody className="grid items-end gap-3 sm:grid-cols-4">
            {unitSelect(f.fromUnitId, (v) => setF({ ...f, fromUnitId: v }), "1 ×", errors.fromUnitId)}
            <Field label="equals" error={errors.factor}>{(p) => <Input {...p} type="number" min={0} step="any" value={f.factor} onChange={(e) => setF({ ...f, factor: e.target.value })} />}</Field>
            {unitSelect(f.toUnitId, (v) => setF({ ...f, toUnitId: v }), "of", errors.toUnitId)}
            <Button onClick={add} icon={<Plus className="size-4" aria-hidden />}>Add</Button>
          </CardBody>
        </Card>
      )}
      <Card>
        <CardHeader title="Converter" />
        <CardBody className="grid items-end gap-3 sm:grid-cols-4">
          <Field label="Quantity">{(p) => <Input {...p} type="number" step="any" value={calc.quantity} onChange={(e) => setCalc({ ...calc, quantity: e.target.value })} />}</Field>
          {unitSelect(calc.fromUnitId, (v) => setCalc({ ...calc, fromUnitId: v }), "From")}
          {unitSelect(calc.toUnitId, (v) => setCalc({ ...calc, toUnitId: v }), "To")}
          <Button variant="secondary" onClick={convert} disabled={!calc.fromUnitId || !calc.toUnitId}>Convert</Button>
          {result && <p className="text-sm font-medium sm:col-span-4" role="status">{result}</p>}
        </CardBody>
      </Card>
    </div>
  );
}
