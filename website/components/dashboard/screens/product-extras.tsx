"use client";

import { Archive, ArchiveRestore, ImagePlus, Pencil, Plus, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { ApiError, fileUrl } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { ProductDetail, Variant } from "@/lib/api/types";
import { date, humanize, qty } from "@/lib/format";
import { useApi, useDashboard } from "../context";

const demoNote = (mode: string) => (mode === "demo" ? " (demo — not saved)" : "");

// ------------------------------------------------------------------ variants

export function VariantsCard({ product, onChange }: { product: ProductDetail; onChange: (p: ProductDetail) => void }) {
  const { client, can } = useDashboard();
  const toast = useToast();
  const [editing, setEditing] = useState<Variant | "new" | null>(null);
  const canEdit = can("PRODUCT_EDIT") && !product.archived;

  const toggle = async (v: Variant) => {
    try {
      onChange(await api.productAdmin.setVariantArchived(client, v.id, !v.archived));
      toast(`${v.archived ? "Restored" : "Archived"} ${v.name}${demoNote(client.mode)}`);
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  return (
    <Card>
      <CardHeader
        title="Variants"
        description="Sizes, colours or other versions, each with its own SKU, barcode and stock."
        action={canEdit && <Button size="sm" variant="secondary" icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing("new")}>Add variant</Button>}
      />
      <DataTable
        caption="Variants"
        rows={product.variants}
        rowKey={(v) => v.id}
        columns={[
          { header: "Variant", cell: (v) => <span>{v.name}{v.isDefault && <span className="text-muted"> (default)</span>}</span> },
          { header: "SKU", cell: (v) => v.sku ?? "—" },
          { header: "Barcode", cell: (v) => v.barcode ?? "—", hideOnMobile: true },
          { header: "Available", align: "right", cell: (v) => `${qty(v.stock.available)} ${product.baseUnit.code}` },
          { header: "Min", align: "right", cell: (v) => qty(v.effectiveMinStock), hideOnMobile: true },
          { header: "Status", cell: (v) => <StatusChip status={v.archived ? "ARCHIVED" : v.status} /> },
          ...(canEdit
            ? [{
                header: "Actions",
                align: "right" as const,
                cell: (v: Variant) => (
                  <div className="flex justify-end gap-1">
                    <button type="button" onClick={() => setEditing(v)} className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={`Edit ${v.name}`}><Pencil className="size-4" aria-hidden /></button>
                    {!v.isDefault && can("PRODUCT_ARCHIVE") && (
                      <button type="button" onClick={() => toggle(v)} className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={v.archived ? `Restore ${v.name}` : `Archive ${v.name}`}>
                        {v.archived ? <ArchiveRestore className="size-4" aria-hidden /> : <Archive className="size-4" aria-hidden />}
                      </button>
                    )}
                  </div>
                ),
              }]
            : []),
        ]}
      />
      <VariantDialog product={product} variant={editing} onClose={() => setEditing(null)} onSaved={onChange} />
    </Card>
  );
}

function VariantDialog({ product, variant, onClose, onSaved }: { product: ProductDetail; variant: Variant | "new" | null; onClose: () => void; onSaved: (p: ProductDetail) => void }) {
  const { client } = useDashboard();
  const toast = useToast();
  const existing = variant && variant !== "new" ? variant : null;
  const [f, setF] = useState({ name: "", sku: "", barcode: "", minStock: "" });
  const [attrs, setAttrs] = useState<{ key: string; value: string }[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [last, setLast] = useState<typeof variant>(null);
  if (variant !== last) {
    setLast(variant);
    setErrors({});
    setFormError(undefined);
    setF({ name: existing?.name ?? "", sku: existing?.sku ?? "", barcode: existing?.barcode ?? "", minStock: existing?.minStock !== undefined ? String(existing.minStock) : "" });
    setAttrs(Object.entries(existing?.attributes ?? {}).map(([key, value]) => ({ key, value: String(value) })));
  }
  const submit = async () => {
    if (!f.name.trim()) return setErrors({ name: "Variant name is required" });
    setSaving(true);
    setFormError(undefined);
    const body = {
      name: f.name.trim(),
      sku: f.sku || undefined,
      barcode: f.barcode || undefined,
      minStock: f.minStock === "" ? null : Number(f.minStock),
      attributes: Object.fromEntries(attrs.filter((a) => a.key.trim()).map((a) => [a.key.trim(), a.value])),
    };
    try {
      onSaved(existing ? await api.productAdmin.updateVariant(client, existing.id, body) : await api.productAdmin.addVariant(client, product.id, body));
      toast(`${existing ? "Variant updated" : "Variant added"}${demoNote(client.mode)}`);
      onClose();
    } catch (e) {
      if (e instanceof ApiError) { setErrors(e.fieldErrors); setFormError(e.message); }
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={variant !== null} onClose={onClose} title={existing ? `Edit ${existing.name}` : "Add variant"} description="e.g. Size: XL, Colour: Blue" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={saving}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {formError && <Notice tone="danger" className="sm:col-span-2">{formError}</Notice>}
        <Field label="Variant name" required error={errors.name} className="sm:col-span-2">{(p) => <Input {...p} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} maxLength={200} />}</Field>
        <Field label="SKU" error={errors.sku}>{(p) => <Input {...p} value={f.sku} onChange={(e) => setF({ ...f, sku: e.target.value })} maxLength={80} />}</Field>
        <Field label="Barcode" error={errors.barcode}>{(p) => <Input {...p} value={f.barcode} onChange={(e) => setF({ ...f, barcode: e.target.value })} maxLength={80} />}</Field>
        <Field label="Minimum stock" hint="Empty uses the product's minimum">{(p) => <Input {...p} type="number" min={0} value={f.minStock} onChange={(e) => setF({ ...f, minStock: e.target.value })} />}</Field>
        <div className="sm:col-span-2">
          <p className="mb-2 text-sm font-medium text-ink-2">Variant details</p>
          <div className="space-y-2">
            {attrs.map((a, i) => (
              <div key={i} className="flex gap-2">
                <Input aria-label="Detail name" placeholder="e.g. Size" value={a.key} onChange={(e) => setAttrs(attrs.map((x, j) => (j === i ? { ...x, key: e.target.value } : x)))} />
                <Input aria-label="Detail value" placeholder="e.g. XL" value={a.value} onChange={(e) => setAttrs(attrs.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))} />
                <button type="button" onClick={() => setAttrs(attrs.filter((_, j) => j !== i))} className="rounded-md p-2 text-muted hover:bg-danger-bg hover:text-danger" aria-label="Remove detail"><Trash2 className="size-4" aria-hidden /></button>
              </div>
            ))}
            <Button size="sm" variant="ghost" icon={<Plus className="size-4" aria-hidden />} onClick={() => setAttrs([...attrs, { key: "", value: "" }])}>Add detail</Button>
          </div>
        </div>
      </div>
    </Dialog>
  );
}

// ------------------------------------------------------------------ images

export function ImagesCard({ product, onChange }: { product: ProductDetail; onChange: (p: ProductDetail) => void }) {
  const { client, can } = useDashboard();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const canEdit = can("PRODUCT_EDIT") && !product.archived;

  const upload = async (file: File) => {
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) return toast("Use a JPEG, PNG or WebP image", "error");
    if (file.size > 5 * 1024 * 1024) return toast("Images must be 5 MB or smaller", "error");
    setBusy(true);
    try {
      onChange(await client.upload<ProductDetail>(`/products/${product.id}/images`, file));
      toast("Image added");
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  };

  if (!canEdit && product.images.length === 0) return null;
  return (
    <Card>
      <CardHeader
        title="Images"
        action={canEdit && (
          <>
            <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" id="product-image" onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
            <Button size="sm" variant="secondary" loading={busy} icon={<ImagePlus className="size-4" aria-hidden />} onClick={() => input.current?.click()}>Add image</Button>
          </>
        )}
      />
      <CardBody>
        {product.images.length === 0 ? (
          <p className="text-sm text-muted">No images yet. JPEG, PNG or WebP up to 5 MB.</p>
        ) : (
          <ul className="grid grid-cols-3 gap-2">
            {product.images.map((img) => (
              <li key={img.id} className="group relative aspect-square overflow-hidden rounded-lg border border-line bg-subtle">
                {/* eslint-disable-next-line @next/next/no-img-element -- served by the backend through the proxy */}
                <img src={fileUrl(img.url)} alt={`${product.name} image`} className="size-full object-cover" loading="lazy" />
                {canEdit && (
                  <button
                    type="button"
                    onClick={async () => {
                      try { onChange(await api.productAdmin.deleteImage(client, product.id, img.id)); toast("Image removed"); } catch (e) { toast((e as Error).message, "error"); }
                    }}
                    className="absolute right-1 top-1 rounded-md bg-white/90 p-1 text-danger opacity-0 shadow group-hover:opacity-100 focus:opacity-100"
                    aria-label="Remove image"
                  >
                    <Trash2 className="size-4" aria-hidden />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

// ------------------------------------------------------------------ batches & serials

export function TrackingCard({ product }: { product: ProductDetail }) {
  const { client } = useDashboard();
  const variants = product.variants.filter((v) => !v.archived);
  const [variantId, setVariantId] = useState(variants[0]?.id);
  const batches = useApi(() => (product.trackBatches && variantId ? api.productAdmin.batches(client, variantId) : Promise.resolve([])), [client, variantId, product.trackBatches]);
  const serials = useApi(() => (product.trackSerials && variantId ? api.productAdmin.serials(client, variantId) : Promise.resolve([])), [client, variantId, product.trackSerials]);
  return (
    <Card>
      <CardHeader
        title={product.trackBatches ? "Batches / lots" : "Serial numbers"}
        action={variants.length > 1 && (
          <>
            <label htmlFor="track-variant" className="sr-only">Variant</label>
            <Select id="track-variant" value={variantId} onChange={(e) => setVariantId(Number(e.target.value))} className="h-9 w-48!">{variants.map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}</Select>
          </>
        )}
      />
      {product.trackBatches && (batches.error ? <ErrorState error={batches.error} onRetry={batches.reload} /> : !batches.data ? <LoadingBlock rows={2} /> : batches.data.length === 0 ? <EmptyState title="No batches yet" /> : (
        <DataTable caption="Batches" rows={batches.data} rowKey={(b) => b.id} columns={[
          { header: "Lot", cell: (b) => b.lotNumber ?? "—" },
          { header: "Expiry", cell: (b) => (b.expiryDate ? <span className={new Date(b.expiryDate) < new Date() ? "font-medium text-danger" : ""}>{date(b.expiryDate)}{new Date(b.expiryDate) < new Date() ? " (expired)" : ""}</span> : "—") },
          { header: "On hand", align: "right", cell: (b) => `${qty(b.onHand)} ${product.baseUnit.code}` },
        ]} />
      ))}
      {product.trackSerials && (serials.error ? <ErrorState error={serials.error} onRetry={serials.reload} /> : !serials.data ? <LoadingBlock rows={2} /> : serials.data.length === 0 ? <EmptyState title="No serial numbers yet" /> : (
        <DataTable caption="Serial numbers" rows={serials.data} rowKey={(s) => s.id} columns={[
          { header: "Serial", cell: (s) => <span className="font-mono">{s.serial}</span> },
          { header: "Status", cell: (s) => humanize(s.status) },
          { header: "Warehouse", cell: (s) => s.warehouseName ?? "—" },
          { header: "Updated", cell: (s) => date(s.updatedAt), hideOnMobile: true },
        ]} />
      ))}
    </Card>
  );
}
