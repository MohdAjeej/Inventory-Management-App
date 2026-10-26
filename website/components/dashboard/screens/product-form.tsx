"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/field";
import { ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Attribute, ProductRequest } from "@/lib/api/types";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";
import { flattenCategories } from "./products";

/** Renders one custom field (TEXT, NUMBER, DATE, BOOLEAN, DROPDOWN, MULTI_SELECT); values are validated by the server. */
export function AttributeInput({ field, value, onChange, error }: { field: Attribute; value: unknown; onChange: (v: unknown) => void; error?: string }) {
  if (field.fieldType === "BOOLEAN") {
    return (
      <div className="space-y-1">
        <Checkbox label={field.label + (field.required ? " *" : "")} checked={value === true} onChange={(e) => onChange(e.target.checked)} />
        {error && <p className="text-xs font-medium text-danger">{error}</p>}
      </div>
    );
  }
  if (field.fieldType === "MULTI_SELECT") {
    const selected = Array.isArray(value) ? (value as string[]) : [];
    return (
      <fieldset className="space-y-1.5">
        <legend className="text-sm font-medium text-ink-2">{field.label}{field.required && <span className="text-danger"> *</span>}</legend>
        <div className="flex flex-wrap gap-2">
          {(field.options ?? []).map((o) => (
            <label key={o} className={`cursor-pointer rounded-full border px-3 py-1 text-sm ${selected.includes(o) ? "border-brand-600 bg-brand-50 text-brand-700" : "border-line-strong"}`}>
              <input type="checkbox" className="sr-only" checked={selected.includes(o)} onChange={(e) => onChange(e.target.checked ? [...selected, o] : selected.filter((x) => x !== o))} />
              {o}
            </label>
          ))}
        </div>
        {error && <p className="text-xs font-medium text-danger">{error}</p>}
      </fieldset>
    );
  }
  return (
    <Field label={field.label} required={field.required} error={error}>
      {(p) =>
        field.fieldType === "DROPDOWN" ? (
          <Select {...p} value={(value as string) ?? ""} onChange={(e) => onChange(e.target.value || null)}>
            <option value="">Choose…</option>
            {(field.options ?? []).map((o) => <option key={o} value={o}>{o}</option>)}
          </Select>
        ) : (
          <Input
            {...p}
            type={field.fieldType === "NUMBER" ? "number" : field.fieldType === "DATE" ? "date" : "text"}
            step={field.fieldType === "NUMBER" ? "any" : undefined}
            value={value === undefined || value === null ? "" : String(value)}
            onChange={(e) => onChange(e.target.value === "" ? null : field.fieldType === "NUMBER" ? Number(e.target.value) : e.target.value)}
          />
        )
      }
    </Field>
  );
}

type FormState = {
  name: string; categoryId: string; baseUnitId: string; brand: string; model: string; sku: string; barcode: string; minStock: string;
  salePrice: string; purchasePrice: string; taxRate: string; supplierId: string; description: string;
  openingQty: string; warehouseId: string; lotNumber: string; expiryDate: string; serials: string;
};

const EMPTY: FormState = { name: "", categoryId: "", baseUnitId: "", brand: "", model: "", sku: "", barcode: "", minStock: "", salePrice: "", purchasePrice: "", taxRate: "", supplierId: "", description: "", openingQty: "", warehouseId: "", lotNumber: "", expiryDate: "", serials: "" };

export function ProductFormScreen({ id }: { id?: number }) {
  const editing = id !== undefined;
  const { client, href, can } = useDashboard();
  const router = useRouter();
  const toast = useToast();
  const existing = useApi(() => (editing ? api.products.get(client, id!) : Promise.resolve(null)), [client, id]);
  const units = useApi(() => api.catalog.units(client), [client]);
  const cats = useApi(() => api.catalog.categories(client), [client]);
  const warehouses = useApi(() => api.warehouses(client), [client]);
  const suppliers = useApi(() => (can("PURCHASE_MANAGE", "PRODUCT_EDIT", "PRODUCT_CREATE") ? api.parties.list(client, "suppliers", {}) : Promise.resolve([])), [client]);
  const settings = useApi(() => api.business.settings(client), [client]);
  const flat = useMemo(() => flattenCategories(cats.data ?? []), [cats.data]);
  const [f, setF] = useState<FormState>(EMPTY);
  const [attrs, setAttrs] = useState<Record<string, unknown>>({});
  const [trackBatches, setTrackBatches] = useState(false);
  const [trackSerials, setTrackSerials] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [loaded, setLoaded] = useState(!editing);
  const schema = useApi(() => api.catalogAdmin.formSchema(client, f.categoryId ? Number(f.categoryId) : null), [client, f.categoryId]);
  const set = (k: keyof FormState) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));
  const str = (v: unknown) => (v === undefined || v === null ? "" : String(v));

  useEffect(() => {
    const p = existing.data;
    if (!p || loaded) return;
    const v = p.variants.find((x) => x.isDefault) ?? p.variants[0];
    setF({ ...EMPTY, name: p.name, categoryId: str(p.categoryId), baseUnitId: str(p.baseUnit.id), brand: str(p.brand), model: str(p.model), sku: str(v?.sku), barcode: str(v?.barcode), minStock: str(p.minStock), salePrice: str(p.salePrice), purchasePrice: str(p.purchasePrice), taxRate: str(p.taxRate), supplierId: str(p.supplierId), description: str(p.description) });
    setAttrs(p.attributes ?? {});
    setTrackBatches(p.trackBatches);
    setTrackSerials(p.trackSerials);
    setLoaded(true);
  }, [existing.data, loaded]);

  useEffect(() => {
    if (units.data && !f.baseUnitId && !editing) setF((x) => ({ ...x, baseUnitId: String(units.data!.find((u) => u.code === "pcs")?.id ?? units.data![0]?.id ?? "") }));
  }, [units.data]); // eslint-disable-line react-hooks/exhaustive-deps

  const allowed = editing ? can("PRODUCT_EDIT") : can("PRODUCT_CREATE");
  const title = editing ? "Edit product" : "Add product";
  const back = editing ? { href: href(`/products/${id}`), label: "Product" } : { href: href("/products"), label: "Products" };
  if (!allowed) return <><PageHeader title={title} back={back} /><Notice tone="warning">You don&apos;t have permission to {editing ? "edit" : "create"} products.</Notice></>;
  if (existing.error) return <Card><ErrorState error={existing.error} onRetry={existing.reload} /></Card>;
  if (!loaded) return <Card><LoadingBlock rows={8} /></Card>;

  const fields = (schema.data?.fields ?? []).filter((a) => !a.archived).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  const batchesOn = settings.data?.trackBatches;
  const serialsOn = settings.data?.trackSerials;
  const showOpening = !editing && can("STOCK_ADD");
  const num = (v: string) => (v.trim() === "" ? null : Number(v));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!f.name.trim()) errs.name = "Product name is required";
    for (const k of ["salePrice", "purchasePrice", "minStock", "openingQty"] as const) if (f[k] && (Number.isNaN(Number(f[k])) || Number(f[k]) < 0)) errs[k] = "Enter 0 or more";
    if (f.taxRate && (Number(f.taxRate) < 0 || Number(f.taxRate) > 100)) errs.taxRate = "Tax rate must be between 0 and 100";
    for (const a of fields) {
      const v = attrs[a.key];
      if (a.required && (v === undefined || v === null || v === "" || (Array.isArray(v) && v.length === 0))) errs[`attributes.${a.key}`] = `${a.label} is required`;
    }
    const serialList = f.serials.split(/[\n,]/).map((x) => x.trim()).filter(Boolean);
    if (!editing && trackSerials && f.openingQty && serialList.length !== Number(f.openingQty)) errs.serials = `Enter exactly ${f.openingQty} serial numbers`;
    setErrors(errs);
    if (Object.keys(errs).length) return;
    // Only send values for fields in the current category's schema.
    const attributes = Object.fromEntries(fields.filter((a) => attrs[a.key] !== undefined && attrs[a.key] !== null && attrs[a.key] !== "").map((a) => [a.key, attrs[a.key]]));
    const body: ProductRequest = {
      name: f.name.trim(), categoryId: num(f.categoryId), baseUnitId: num(f.baseUnitId), brand: f.brand || undefined, model: f.model || undefined,
      sku: f.sku || undefined, barcode: f.barcode || undefined, minStock: num(f.minStock), salePrice: num(f.salePrice), purchasePrice: num(f.purchasePrice),
      taxRate: num(f.taxRate), supplierId: num(f.supplierId), description: f.description || undefined, attributes, trackBatches, trackSerials,
      openingStock: !editing && can("STOCK_ADD") && f.openingQty
        ? { quantity: Number(f.openingQty), warehouseId: num(f.warehouseId), lotNumber: trackBatches ? f.lotNumber || undefined : undefined, expiryDate: trackBatches && f.expiryDate ? f.expiryDate : undefined, serials: trackSerials ? serialList : undefined }
        : null,
    };
    setSaving(true);
    setFormError(undefined);
    try {
      const saved = editing ? await api.productAdmin.update(client, id!, body) : await api.products.create(client, body);
      toast(`${editing ? "Product updated" : "Product added"}${client.mode === "demo" ? " (demo — not saved)" : ""}`);
      router.push(href(`/products/${saved.id}`));
    } catch (err) {
      if (err instanceof ApiError) { setErrors(err.fieldErrors); setFormError(err.message); } else setFormError("Something went wrong");
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader title={title} description="Only the name is required. The category's custom fields appear automatically." back={back} />
      <form onSubmit={submit} noValidate className="space-y-6">
        {formError && <Notice tone="danger">{formError}</Notice>}
        <Card>
          <CardHeader title="Basics" />
          <CardBody className="grid gap-4 sm:grid-cols-2">
            <Field label="Product name" required error={errors.name} className="sm:col-span-2">{(p) => <Input {...p} value={f.name} onChange={set("name")} maxLength={200} />}</Field>
            <Field label="Category" error={errors.categoryId}>
              {(p) => <Select {...p} value={f.categoryId} onChange={set("categoryId")}><option value="">Uncategorized</option>{flat.filter((c) => !c.archived).map((c) => <option key={c.id} value={c.id}>{c.path}</option>)}</Select>}
            </Field>
            <Field label="Unit" error={errors.baseUnitId} hint={editing ? "Changing the unit is refused once stock exists." : undefined}>
              {(p) => <Select {...p} value={f.baseUnitId} onChange={set("baseUnitId")}>{(units.data ?? []).filter((u) => !u.archived).map((u) => <option key={u.id} value={u.id}>{u.name} ({u.code})</option>)}</Select>}
            </Field>
            <Field label="Brand">{(p) => <Input {...p} value={f.brand} onChange={set("brand")} maxLength={120} />}</Field>
            <Field label="Model">{(p) => <Input {...p} value={f.model} onChange={set("model")} maxLength={120} />}</Field>
            <Field label="SKU" error={errors.sku}>{(p) => <Input {...p} value={f.sku} onChange={set("sku")} maxLength={80} />}</Field>
            <Field label="Barcode" error={errors.barcode}>{(p) => <Input {...p} value={f.barcode} onChange={set("barcode")} maxLength={80} />}</Field>
            <Field label="Description" className="sm:col-span-2">{(p) => <Textarea {...p} value={f.description} onChange={set("description")} maxLength={2000} />}</Field>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Stock" />
          <CardBody className="grid gap-4 sm:grid-cols-2">
            {showOpening && <Field label="Opening stock" error={errors.openingQty || errors["openingStock.quantity"]} hint="How many you have right now. Leave empty for none.">{(p) => <Input {...p} type="number" min={0} step="any" placeholder="e.g. 50" value={f.openingQty} onChange={set("openingQty")} />}</Field>}
            <Field label="Minimum stock level" error={errors.minStock} hint="You get a low-stock alert at this level. Leave empty to use the business default.">{(p) => <Input {...p} type="number" min={0} placeholder="e.g. 10" value={f.minStock} onChange={set("minStock")} />}</Field>
            {showOpening && (warehouses.data?.length ?? 0) > 1 && <Field label="Warehouse" hint="Where the opening stock is kept">{(p) => <Select {...p} value={f.warehouseId} onChange={set("warehouseId")}>{(warehouses.data ?? []).map((w) => <option key={w.id} value={w.isDefault ? "" : w.id}>{w.name}{w.isDefault ? " (default)" : ""}</option>)}</Select>}</Field>}
            {showOpening && f.openingQty && trackBatches && <Field label="Lot number" error={errors["openingStock.lotNumber"]}>{(p) => <Input {...p} value={f.lotNumber} onChange={set("lotNumber")} maxLength={80} />}</Field>}
            {showOpening && f.openingQty && trackBatches && settings.data?.trackExpiry && <Field label="Expiry date">{(p) => <Input {...p} type="date" value={f.expiryDate} onChange={set("expiryDate")} />}</Field>}
            {showOpening && f.openingQty && trackSerials && <Field label="Serial numbers" className="sm:col-span-2" hint="One per line or comma-separated; one per unit" error={errors.serials || errors["openingStock.serials"]}>{(p) => <Textarea {...p} value={f.serials} onChange={set("serials")} />}</Field>}
          </CardBody>
        </Card>

        {fields.length > 0 && (
          <Card>
            <CardHeader title="Custom fields" description="Defined in Categories & fields. Required fields are marked with *." />
            <CardBody className="grid gap-4 sm:grid-cols-2">
              {fields.map((a) => <AttributeInput key={a.id} field={a} value={attrs[a.key]} onChange={(v) => setAttrs((x) => ({ ...x, [a.key]: v }))} error={errors[`attributes.${a.key}`]} />)}
            </CardBody>
          </Card>
        )}

        <Card>
          <CardHeader title="Prices" description="Purchase price (what you pay your supplier/dealer) and selling price are separate. Prices exclude tax." />
          <CardBody className="grid gap-4 sm:grid-cols-2">
            <Field label="Selling price" error={errors.salePrice}>{(p) => <Input {...p} type="number" min={0} step="0.01" value={f.salePrice} onChange={set("salePrice")} />}</Field>
            <Field label="Purchase price" error={errors.purchasePrice} hint="Used for profit until purchases are recorded">{(p) => <Input {...p} type="number" min={0} step="0.01" value={f.purchasePrice} onChange={set("purchasePrice")} />}</Field>
            <Field label="Tax rate (%)" error={errors.taxRate}>{(p) => <Input {...p} type="number" min={0} max={100} step="0.01" value={f.taxRate} onChange={set("taxRate")} />}</Field>
            <Field label="Supplier">{(p) => <Select {...p} value={f.supplierId} onChange={set("supplierId")}><option value="">None</option>{(suppliers.data ?? []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</Select>}</Field>
          </CardBody>
        </Card>

        {(batchesOn || serialsOn) && (
          <Card>
            <CardHeader title="Tracking" description="Turned on for your business in Business settings." />
            <CardBody className="space-y-3">
              {batchesOn && <Checkbox label="Track batches / lots" description="Record a lot number (and expiry) whenever stock comes in" checked={trackBatches} disabled={editing} onChange={(e) => setTrackBatches(e.target.checked)} />}
              {serialsOn && <Checkbox label="Track serial numbers" description="Each unit has its own serial number" checked={trackSerials} disabled={editing} onChange={(e) => setTrackSerials(e.target.checked)} />}
              {editing && <p className="text-xs text-muted">Tracking can only be chosen when the product is created.</p>}
            </CardBody>
          </Card>
        )}

        <div className="flex justify-end gap-2">
          <ButtonLink href={back.href} variant="secondary">Cancel</ButtonLink>
          <Button type="submit" loading={saving}>{editing ? "Save changes" : "Save product"}</Button>
        </div>
      </form>
    </>
  );
}
