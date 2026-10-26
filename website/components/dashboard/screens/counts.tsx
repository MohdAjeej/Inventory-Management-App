"use client";

import { ClipboardCheck, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable, Pagination } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { api } from "@/lib/api/endpoints";
import type { StockCountSummary } from "@/lib/api/types";
import { dateTime, qty } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";
import { flattenCategories } from "./products";

const STATUS_LABEL: Record<string, string> = { OPEN: "PENDING", RECONCILED: "COMPLETED", CANCELLED: "CANCELLED" };

export function StockCountsScreen() {
  const { client, href, can } = useDashboard();
  const router = useRouter();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const list = useApi(() => api.counts.list(client, { page, size: 20 }), [client, page]);
  const warehouses = useApi(() => api.warehouses(client), [client]);
  const cats = useApi(() => api.catalog.categories(client), [client]);
  const [open, setOpen] = useState(false);
  const [f, setF] = useState({ warehouseId: "", categoryId: "", notes: "" });
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  useEffect(() => { const d = warehouses.data?.find((w) => w.isDefault); if (d && !f.warehouseId) setF((x) => ({ ...x, warehouseId: String(d.id) })); }, [warehouses.data]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!can("STOCK_COUNT")) return <><PageHeader title="Stock counts" /><Notice tone="warning">You need the “Run stock counts” permission.</Notice></>;

  const create = async () => {
    if (!f.warehouseId) return setError("Choose a warehouse");
    setSaving(true);
    try {
      const c = await api.counts.create(client, { warehouseId: Number(f.warehouseId), categoryId: f.categoryId ? Number(f.categoryId) : null, notes: f.notes || undefined });
      toast("Stock count started; expected quantities were snapshotted");
      router.push(href(`/stock-counts/${c.summary.id}`));
    } catch (e) {
      setError((e as Error).message);
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader title="Stock counts" description="Snapshot what the system expects, count what's on the shelf, then reconcile the differences into adjustments." actions={<Button icon={<Plus className="size-4" aria-hidden />} onClick={() => setOpen(true)}>New count</Button>} />
      <Card>
        {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : !list.data ? <LoadingBlock /> : list.data.content.length === 0 ? <EmptyState icon={ClipboardCheck} title="No stock counts yet" description="Start a count for a warehouse (optionally one category)." /> : (
          <>
            <DataTable<StockCountSummary> caption="Stock counts" rows={list.data.content} rowKey={(c) => c.id} rowHref={(c) => href(`/stock-counts/${c.id}`)} columns={[
              { header: "Count", cell: (c) => `#${c.id} · ${c.warehouseName}` },
              { header: "Started", cell: (c) => dateTime(c.createdAt) },
              { header: "Progress", align: "right", cell: (c) => `${c.countedLines} / ${c.lineCount} counted` },
              { header: "By", cell: (c) => c.createdBy ?? "—", hideOnMobile: true },
              { header: "Status", cell: (c) => <StatusChip status={STATUS_LABEL[c.status]} /> },
            ]} />
            <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
      <Dialog open={open} onClose={() => setOpen(false)} title="New stock count" footer={<><Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button><Button onClick={create} loading={saving}>Start count</Button></>}>
        <div className="space-y-4">
          {error && <Notice tone="danger">{error}</Notice>}
          <Field label="Warehouse" required>{(p) => <Select {...p} value={f.warehouseId} onChange={(e) => setF({ ...f, warehouseId: e.target.value })}>{(warehouses.data ?? []).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</Select>}</Field>
          <Field label="Category (optional)">{(p) => <Select {...p} value={f.categoryId} onChange={(e) => setF({ ...f, categoryId: e.target.value })}><option value="">All products</option>{flattenCategories(cats.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.path}</option>)}</Select>}</Field>
          <Field label="Notes">{(p) => <Textarea {...p} value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} maxLength={1000} className="min-h-16" />}</Field>
        </div>
      </Dialog>
    </>
  );
}

export function StockCountDetailScreen({ id }: { id: number }) {
  const { client, href, can } = useDashboard();
  const toast = useToast();
  const count = useApi(() => api.counts.get(client, id), [client, id]);
  const [values, setValues] = useState<Record<number, string>>({});
  const [filter, setFilter] = useState<"all" | "uncounted" | "diff">("all");
  const [busy, setBusy] = useState<string>();
  const [confirm, setConfirm] = useState(false);

  useEffect(() => {
    if (count.data) setValues(Object.fromEntries(count.data.lines.map((l) => [l.id, l.countedQty === undefined || l.countedQty === null ? "" : String(l.countedQty)])));
  }, [count.data]);

  const c = count.data;
  const dirty = useMemo(() => (c ? c.lines.filter((l) => (values[l.id] ?? "") !== (l.countedQty === undefined || l.countedQty === null ? "" : String(l.countedQty))) : []), [c, values]);
  if (count.error) return <Card><ErrorState error={count.error} onRetry={count.reload} /></Card>;
  if (!c) return <Card><LoadingBlock rows={8} /></Card>;
  const open = c.summary.status === "OPEN";
  const rows = c.lines.filter((l) => filter === "all" || (filter === "uncounted" ? (values[l.id] ?? "") === "" : (values[l.id] ?? "") !== "" && Number(values[l.id]) !== l.expectedQty));
  const differences = c.lines.filter((l) => (values[l.id] ?? "") !== "" && Number(values[l.id]) !== l.expectedQty).length;

  const save = async () => {
    setBusy("save");
    try {
      count.setData(await api.counts.updateLines(client, id, dirty.map((l) => ({ lineId: l.id, countedQty: values[l.id] === "" ? null : Number(values[l.id]) }))));
      toast(`Saved ${dirty.length} counted line${dirty.length === 1 ? "" : "s"}`);
    } catch (e) { toast((e as Error).message, "error"); } finally { setBusy(undefined); }
  };
  const reconcile = async () => {
    setBusy("reconcile");
    try {
      if (dirty.length) await api.counts.updateLines(client, id, dirty.map((l) => ({ lineId: l.id, countedQty: values[l.id] === "" ? null : Number(values[l.id]) })));
      const r = await api.counts.reconcile(client, id);
      toast(`Reconciled: ${r.adjustedLines} adjustment${r.adjustedLines === 1 ? "" : "s"} recorded`);
      setConfirm(false);
      count.reload();
    } catch (e) { toast((e as Error).message, "error"); } finally { setBusy(undefined); }
  };
  const cancel = async () => {
    setBusy("cancel");
    try { await api.counts.cancel(client, id); toast("Count cancelled"); count.reload(); } catch (e) { toast((e as Error).message, "error"); } finally { setBusy(undefined); }
  };

  return (
    <>
      <PageHeader
        back={{ href: href("/stock-counts"), label: "Stock counts" }}
        title={`Stock count #${c.summary.id} · ${c.summary.warehouseName}`}
        description={`Started ${dateTime(c.summary.createdAt)}${c.summary.createdBy ? ` by ${c.summary.createdBy}` : ""} · ${c.summary.countedLines} of ${c.summary.lineCount} counted`}
        actions={open && (
          <>
            <Button variant="secondary" loading={busy === "save"} disabled={!dirty.length} onClick={save}>Save counts{dirty.length ? ` (${dirty.length})` : ""}</Button>
            {can("STOCK_ADJUST") && <Button loading={busy === "reconcile"} onClick={() => setConfirm(true)}>Reconcile</Button>}
            <Button variant="ghost" loading={busy === "cancel"} onClick={cancel}>Cancel count</Button>
          </>
        )}
      />
      {!open && <Notice tone={c.summary.status === "RECONCILED" ? "success" : "warning"} className="mb-4">{c.summary.status === "RECONCILED" ? `Reconciled ${dateTime(c.summary.reconciledAt)}. Differences were recorded as count adjustments.` : "This count was cancelled. No stock changed."}</Notice>}
      {open && !can("STOCK_ADJUST") && <Notice className="mb-4">You can enter counts. Reconciling needs the “Adjust stock” permission.</Notice>}
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line p-4">
          <label htmlFor="count-filter" className="sr-only">Show</label>
          <Select id="count-filter" value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)} className="w-56!">
            <option value="all">All lines ({c.lines.length})</option>
            <option value="uncounted">Not counted yet</option>
            <option value="diff">With differences ({differences})</option>
          </Select>
          <p className="text-sm text-muted">Uncounted lines are left unchanged when reconciling.</p>
        </div>
        {rows.length === 0 ? <EmptyState title="No lines to show" /> : (
          <DataTable caption="Count lines" rows={rows} rowKey={(l) => l.id} columns={[
            { header: "Product", cell: (l) => <span>{l.productName}{l.variantName && l.variantName !== l.productName ? ` · ${l.variantName}` : ""}{l.lotNumber && <span className="text-muted"> · Lot {l.lotNumber}</span>}</span> },
            { header: "Expected", align: "right", cell: (l) => `${qty(l.expectedQty)} ${l.unitCode}` },
            {
              header: "Counted",
              align: "right",
              cell: (l) => open ? (
                <Input aria-label={`Counted quantity for ${l.productName}`} type="number" min={0} step="any" inputMode="decimal" className="ml-auto h-9 w-28! text-right" value={values[l.id] ?? ""} onChange={(e) => setValues((v) => ({ ...v, [l.id]: e.target.value }))} />
              ) : l.countedQty === undefined || l.countedQty === null ? "—" : qty(l.countedQty),
            },
            {
              header: "Difference",
              align: "right",
              cell: (l) => {
                const v = values[l.id];
                if (v === undefined || v === "") return "—";
                const d = Number(v) - l.expectedQty;
                return <span className={d === 0 ? "text-muted" : d > 0 ? "font-medium text-success" : "font-medium text-danger"}>{d > 0 ? "+" : ""}{qty(d)}</span>;
              },
            },
          ]} />
        )}
      </Card>
      <Dialog open={confirm} onClose={() => setConfirm(false)} title="Reconcile this count?" description="Every counted line with a difference becomes a count adjustment movement. This can't be undone." footer={<><Button variant="secondary" onClick={() => setConfirm(false)}>Back</Button><Button loading={busy === "reconcile"} onClick={reconcile}>Reconcile {differences} difference{differences === 1 ? "" : "s"}</Button></>}>
        <CardBody className="p-0 text-sm text-ink-2">{c.lines.length - c.lines.filter((l) => (values[l.id] ?? "") !== "").length} uncounted line(s) will be left unchanged.</CardBody>
      </Dialog>
    </>
  );
}
