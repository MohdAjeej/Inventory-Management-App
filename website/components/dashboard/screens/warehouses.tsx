"use client";

import { Archive, ArchiveRestore, MapPin, Pencil, Plus, Star, Warehouse as WarehouseIcon } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Badge, StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader, DescriptionList } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Checkbox, Field, Input } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable, Pagination } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { InventoryRow, Location, Warehouse } from "@/lib/api/types";
import { qty } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";

const demoNote = (mode: string) => (mode === "demo" ? " (demo — not saved)" : "");

export function WarehousesScreen() {
  const { client, href, can } = useDashboard();
  const toast = useToast();
  const list = useApi(() => api.warehouses(client, true), [client]);
  const [editing, setEditing] = useState<Warehouse | "new" | null>(null);
  const manage = can("WAREHOUSE_MANAGE");

  const act = async (fn: () => Promise<unknown>, msg: string) => {
    try { await fn(); toast(msg + demoNote(client.mode)); list.reload(); } catch (e) { toast((e as Error).message, "error"); }
  };

  return (
    <>
      <PageHeader title="Warehouses & branches" description="Stock is tracked separately in each warehouse or branch." actions={manage && <Button icon={<Plus className="size-4" aria-hidden />} onClick={() => setEditing("new")}>Add warehouse</Button>} />
      {list.error ? <Card><ErrorState error={list.error} onRetry={list.reload} /></Card> : !list.data ? <Card><LoadingBlock /></Card> : list.data.length === 0 ? <Card><EmptyState title="No warehouses" /></Card> : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {list.data.map((w) => (
            <Card key={w.id} className={w.archived ? "opacity-70" : undefined}>
              <CardBody>
                <div className="flex items-start justify-between gap-3">
                  <Link href={href(`/warehouses/${w.id}`)} className="flex min-w-0 items-center gap-3 hover:underline">
                    <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-600"><WarehouseIcon className="size-5" aria-hidden /></span>
                    <span className="min-w-0">
                      <span className="block truncate font-semibold">{w.name}</span>
                      <span className="text-xs text-muted">{w.code} · {w.branch ? "Branch" : "Warehouse"}</span>
                    </span>
                  </Link>
                  {w.isDefault && <Badge tone="info" icon={<Star className="size-3" aria-hidden />}>Default</Badge>}
                  {w.archived && <StatusChip status="ARCHIVED" />}
                </div>
                {w.address && <p className="mt-3 text-sm text-ink-2">{w.address}</p>}
                <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-line pt-3 text-sm">
                  <div><dt className="text-xs text-muted">Products in stock</dt><dd className="font-semibold tabular">{w.productCount}</dd></div>
                  <div><dt className="text-xs text-muted">Total quantity</dt><dd className="font-semibold tabular">{qty(w.totalQuantity)}</dd></div>
                </dl>
                {manage && (
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Button size="sm" variant="secondary" icon={<Pencil className="size-3.5" aria-hidden />} onClick={() => setEditing(w)}>Edit</Button>
                    {!w.isDefault && !w.archived && <Button size="sm" variant="ghost" onClick={() => act(() => api.warehouseAdmin.makeDefault(client, w.id), `${w.name} is now the default`)}>Make default</Button>}
                    {!w.isDefault && (
                      <Button size="sm" variant="ghost" icon={w.archived ? <ArchiveRestore className="size-3.5" aria-hidden /> : <Archive className="size-3.5" aria-hidden />} onClick={() => act(() => api.warehouseAdmin.setArchived(client, w.id, !w.archived), w.archived ? "Warehouse restored" : "Warehouse archived")}>
                        {w.archived ? "Restore" : "Archive"}
                      </Button>
                    )}
                  </div>
                )}
              </CardBody>
            </Card>
          ))}
        </div>
      )}
      <p className="mt-4 text-sm text-muted">A warehouse can only be archived when it is empty. The default warehouse is used when no other is chosen.</p>
      <WarehouseDialog warehouse={editing} onClose={() => setEditing(null)} onSaved={list.reload} />
    </>
  );
}

function WarehouseDialog({ warehouse, onClose, onSaved }: { warehouse: Warehouse | "new" | null; onClose: () => void; onSaved: () => void }) {
  const { client } = useDashboard();
  const toast = useToast();
  const existing = warehouse && warehouse !== "new" ? warehouse : null;
  const [f, setF] = useState({ name: "", code: "", address: "", branch: false });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [last, setLast] = useState<typeof warehouse>(null);
  if (warehouse !== last) {
    setLast(warehouse);
    setErrors({});
    setFormError(undefined);
    setF({ name: existing?.name ?? "", code: existing?.code ?? "", address: existing?.address ?? "", branch: existing?.branch ?? false });
  }
  const submit = async () => {
    const errs: Record<string, string> = {};
    if (!f.name.trim()) errs.name = "Name is required";
    if (!f.code.trim()) errs.code = "Code is required";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    try {
      const body = { name: f.name.trim(), code: f.code.trim().toUpperCase(), address: f.address || undefined, branch: f.branch };
      if (existing) await api.warehouseAdmin.update(client, existing.id, body);
      else await api.warehouseAdmin.create(client, body);
      toast(`Warehouse saved${demoNote(client.mode)}`);
      onSaved();
      onClose();
    } catch (e) {
      if (e instanceof ApiError) { setErrors(e.fieldErrors); setFormError(e.message); }
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={warehouse !== null} onClose={onClose} title={existing ? `Edit ${existing.name}` : "Add warehouse"} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={saving}>Save</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        {formError && <Notice tone="danger" className="sm:col-span-2">{formError}</Notice>}
        <Field label="Name" required error={errors.name}>{(p) => <Input {...p} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} maxLength={120} />}</Field>
        <Field label="Code" required error={errors.code} hint="Short and unique, e.g. CITY">{(p) => <Input {...p} className="uppercase" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} maxLength={30} />}</Field>
        <Field label="Address" className="sm:col-span-2">{(p) => <Input {...p} value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} maxLength={500} />}</Field>
        <Checkbox label="This is a branch (shop or outlet)" checked={f.branch} onChange={(e) => setF({ ...f, branch: e.target.checked })} />
      </div>
    </Dialog>
  );
}

export function WarehouseDetailScreen({ id }: { id: number }) {
  const { client, href, can } = useDashboard();
  const toast = useToast();
  const list = useApi(() => api.warehouses(client, true), [client]);
  const locations = useApi(() => api.warehouseAdmin.locations(client, id), [client, id]);
  const [page, setPage] = useState(0);
  const stock = useApi(() => (can("REPORT_VIEW") ? api.reports.inventory(client, { warehouseId: id, page, size: 25 }) : Promise.resolve(null)), [client, id, page]);
  const [editing, setEditing] = useState<Location | "new" | null>(null);
  const [f, setF] = useState({ code: "", description: "" });
  const [error, setError] = useState<string>();
  const manage = can("WAREHOUSE_MANAGE");
  const w = list.data?.find((x) => x.id === id);

  if (list.error) return <Card><ErrorState error={list.error} onRetry={list.reload} /></Card>;
  if (!list.data) return <Card><LoadingBlock /></Card>;
  if (!w) return <Card><EmptyState title="Warehouse not found" /></Card>;

  const openLoc = (l: Location | "new") => { setEditing(l); setError(undefined); setF(l === "new" ? { code: "", description: "" } : { code: l.code, description: l.description ?? "" }); };
  const saveLoc = async () => {
    if (!f.code.trim()) return setError("Code is required");
    try {
      if (editing === "new") await api.warehouseAdmin.createLocation(client, id, { code: f.code.trim(), description: f.description || undefined });
      else if (editing) await api.warehouseAdmin.updateLocation(client, editing.id, { code: f.code.trim(), description: f.description || undefined });
      toast(`Location saved${demoNote(client.mode)}`);
      locations.reload();
      setEditing(null);
    } catch (e) { setError((e as Error).message); }
  };

  return (
    <>
      <PageHeader back={{ href: href("/warehouses"), label: "Warehouses" }} title={w.name} description={`${w.code} · ${w.branch ? "Branch" : "Warehouse"}${w.isDefault ? " · Default" : ""}`} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {stock.data && (
            <Card>
              <CardHeader title="Stock in this warehouse" />
              {stock.data.content.length === 0 ? <EmptyState title="No stock here yet" /> : (
                <>
                  <DataTable<InventoryRow> caption="Stock" rows={stock.data.content} rowKey={(r) => r.variantId} rowHref={(r) => href(`/products/${r.productId}`)} columns={[
                    { header: "Product", cell: (r) => r.productName },
                    { header: "On hand", align: "right", cell: (r) => qty(r.onHand) },
                    { header: "Available", align: "right", cell: (r) => `${qty(r.available)} ${r.unit}` },
                    { header: "Status", cell: (r) => <StatusChip status={r.status} /> },
                  ]} />
                  <Pagination page={stock.data.page} totalPages={stock.data.totalPages} totalElements={stock.data.totalElements} onPage={setPage} />
                </>
              )}
            </Card>
          )}
          <Card>
            <CardHeader title="Storage locations" description="Racks, shelves or bins. Stock balances are tracked per warehouse, not per location." action={manage && <Button size="sm" variant="secondary" icon={<Plus className="size-4" aria-hidden />} onClick={() => openLoc("new")}>Add location</Button>} />
            {locations.error ? <ErrorState error={locations.error} onRetry={locations.reload} /> : !locations.data ? <LoadingBlock rows={2} /> : locations.data.length === 0 ? <EmptyState icon={MapPin} title="No locations yet" /> : (
              <ul className="divide-y divide-line">
                {locations.data.map((l) => (
                  <li key={l.id} className={`flex items-center justify-between gap-3 px-5 py-3 text-sm ${l.archived ? "opacity-60" : ""}`}>
                    <span><span className="font-mono font-medium">{l.code}</span>{l.description && <span className="text-muted"> · {l.description}</span>}{l.archived && <> <StatusChip status="ARCHIVED" /></>}</span>
                    {manage && (
                      <span className="flex gap-1">
                        <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={`Edit ${l.code}`} onClick={() => openLoc(l)}><Pencil className="size-4" aria-hidden /></button>
                        <button type="button" className="rounded-md p-1.5 text-muted hover:bg-subtle hover:text-ink" aria-label={l.archived ? `Restore ${l.code}` : `Archive ${l.code}`} onClick={async () => { try { await api.warehouseAdmin.setLocationArchived(client, l.id, !l.archived); locations.reload(); } catch (e) { toast((e as Error).message, "error"); } }}>{l.archived ? <ArchiveRestore className="size-4" aria-hidden /> : <Archive className="size-4" aria-hidden />}</button>
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
        <Card>
          <CardHeader title="Details" />
          <CardBody><DescriptionList className="sm:grid-cols-1" items={[{ label: "Address", value: w.address }, { label: "Products in stock", value: w.productCount }, { label: "Total quantity", value: qty(w.totalQuantity) }, { label: "Status", value: w.archived ? "Archived" : "Active" }]} /></CardBody>
        </Card>
      </div>
      <Dialog open={editing !== null} onClose={() => setEditing(null)} title={editing === "new" ? "Add location" : "Edit location"} footer={<><Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button><Button onClick={saveLoc}>Save</Button></>}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Code" required error={error}>{(p) => <Input {...p} value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} maxLength={40} placeholder="e.g. A-01" />}</Field>
          <Field label="Description">{(p) => <Input {...p} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} maxLength={200} />}</Field>
        </div>
      </Dialog>
    </>
  );
}
