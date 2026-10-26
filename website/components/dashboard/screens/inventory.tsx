"use client";

import { Boxes } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Badge, StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { DataTable, Pagination } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { api } from "@/lib/api/endpoints";
import type { InventoryRow, Movement, MovementType } from "@/lib/api/types";
import { dateTime, humanize, qty } from "@/lib/format";
import { useApi, useDashboard, useDebounced } from "../context";
import { PageHeader, SearchBox } from "../parts";
import { StockOperationDialog } from "../stock-dialog";

// ------------------------------------------------------------------ stock

const MOVEMENT_FILTERS: { label: string; types: MovementType[] }[] = [
  { label: "All movements", types: [] },
  { label: "Sales & returns", types: ["SALE", "SALE_CANCEL", "RETURN_IN"] },
  { label: "Purchases", types: ["PURCHASE", "PURCHASE_CANCEL", "PURCHASE_RETURN"] },
  { label: "Manual add / reduce", types: ["ADD", "REDUCE"] },
  { label: "Adjustments & counts", types: ["ADJUST", "COUNT_ADJUST", "DAMAGE"] },
  { label: "Transfers", types: ["TRANSFER_IN", "TRANSFER_OUT"] },
  { label: "Reservations", types: ["RESERVE", "RELEASE"] },
];

export function StockScreen() {
  const { can, href } = useDashboard();
  const [tab, setTab] = useState<"levels" | "history">(can("REPORT_VIEW") ? "levels" : "history");
  const [open, setOpen] = useState(false);
  const [version, setVersion] = useState(0);
  return (
    <>
      <PageHeader
        title="Inventory"
        description="Stock levels and the permanent history of every stock change."
        actions={can("STOCK_ADD", "STOCK_REDUCE", "STOCK_ADJUST", "STOCK_TRANSFER") && <Button icon={<Boxes className="size-4" aria-hidden />} onClick={() => setOpen(true)}>Update stock</Button>}
      />
      <Card>
        <div className="px-4 pt-2">
          <Tabs
            label="Inventory sections"
            value={tab}
            onChange={setTab}
            tabs={[...(can("REPORT_VIEW") ? [{ key: "levels" as const, label: "Stock" }] : []), { key: "history" as const, label: "Stock activity" }]}
          />
        </div>
        {tab === "levels" ? <StockLevels key={version} /> : <StockHistory key={version} />}
      </Card>
      {!can("REPORT_VIEW") && (
        <p className="mt-3 text-sm text-muted">
          Per-product stock levels are also on the <Link className="font-medium text-brand-700 hover:underline" href={href("/products")}>Products</Link> screen.
        </p>
      )}
      <StockOperationDialog open={open} onClose={() => setOpen(false)} onDone={() => setVersion((v) => v + 1)} />
    </>
  );
}

function StockLevels() {
  const { client, href } = useDashboard();
  const [q, setQ] = useState("");
  const dq = useDebounced(q);
  const [status, setStatus] = useState("");
  const [warehouseId, setWarehouseId] = useState("");
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [dq, status, warehouseId]);
  const warehouses = useApi(() => api.warehouses(client), [client]);
  const rows = useApi(() => api.reports.inventory(client, { q: dq, status, warehouseId, page, size: 25 }), [client, dq, status, warehouseId, page]);
  return (
    <>
      <div className="flex flex-col gap-3 border-b border-line p-4 sm:flex-row">
        <SearchBox value={q} onChange={setQ} placeholder="Search product or SKU" label="Search stock" />
        <div className="grid grid-cols-2 gap-2 sm:ml-auto sm:flex">
          <label htmlFor="sl-wh" className="sr-only">Warehouse</label>
          <Select id="sl-wh" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} className="sm:w-44">
            <option value="">All warehouses</option>
            {(warehouses.data ?? []).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
          </Select>
          <label htmlFor="sl-st" className="sr-only">Status</label>
          <Select id="sl-st" value={status} onChange={(e) => setStatus(e.target.value)} className="sm:w-40">
            <option value="">All levels</option>
            <option value="IN_STOCK">In stock</option>
            <option value="LOW_STOCK">Low stock</option>
            <option value="OUT_OF_STOCK">Out of stock</option>
          </Select>
        </div>
      </div>
      {rows.error ? <ErrorState error={rows.error} onRetry={rows.reload} /> : !rows.data ? <LoadingBlock rows={6} /> : rows.data.content.length === 0 ? <EmptyState title="No matching stock" /> : (
        <>
          <DataTable<InventoryRow>
            caption="Stock levels"
            rows={rows.data.content}
            rowKey={(r) => r.variantId}
            rowHref={(r) => href(`/products/${r.productId}`)}
            columns={[
              { header: "Product", cell: (r) => (r.variantName && r.variantName !== r.productName ? `${r.productName} · ${r.variantName}` : r.productName) },
              { header: "Category", cell: (r) => r.category ?? "—", hideOnMobile: true },
              { header: "On hand", align: "right", cell: (r) => qty(r.onHand) },
              { header: "Reserved", align: "right", cell: (r) => qty(r.reserved), hideOnMobile: true },
              { header: "Damaged", align: "right", cell: (r) => qty(r.damaged), hideOnMobile: true },
              { header: "Available", align: "right", cell: (r) => <strong>{qty(r.available)} {r.unit}</strong> },
              { header: "Minimum", align: "right", cell: (r) => qty(r.minStock), hideOnMobile: true },
              { header: "Status", cell: (r) => <StatusChip status={r.status} /> },
            ]}
          />
          <Pagination page={rows.data.page} totalPages={rows.data.totalPages} totalElements={rows.data.totalElements} onPage={setPage} />
        </>
      )}
    </>
  );
}

function StockHistory() {
  const { client, href } = useDashboard();
  const [filter, setFilter] = useState(0);
  const [warehouseId, setWarehouseId] = useState("");
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [filter, warehouseId]);
  const warehouses = useApi(() => api.warehouses(client), [client]);
  const rows = useApi(() => api.stock.movements(client, { types: MOVEMENT_FILTERS[filter].types, warehouseId, page, size: 30 }), [client, filter, warehouseId, page]);
  return (
    <>
      <div className="grid grid-cols-1 gap-2 border-b border-line p-4 sm:flex">
        <label htmlFor="sh-type" className="sr-only">Movement type</label>
        <Select id="sh-type" value={filter} onChange={(e) => setFilter(Number(e.target.value))} className="sm:w-56">
          {MOVEMENT_FILTERS.map((m, i) => <option key={m.label} value={i}>{m.label}</option>)}
        </Select>
        <label htmlFor="sh-wh" className="sr-only">Warehouse</label>
        <Select id="sh-wh" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} className="sm:w-48">
          <option value="">All warehouses</option>
          {(warehouses.data ?? []).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
        </Select>
      </div>
      {rows.error ? <ErrorState error={rows.error} onRetry={rows.reload} /> : !rows.data ? <LoadingBlock rows={6} /> : rows.data.content.length === 0 ? <EmptyState title="No movements found" /> : (
        <>
          <DataTable<Movement>
            caption="Stock history"
            rows={rows.data.content}
            rowKey={(m) => m.id}
            rowHref={(m) => href(`/products/${m.productId}`)}
            columns={[
              { header: "Product", cell: (m) => m.productName },
              { header: "Type", cell: (m) => <Badge tone={m.quantity >= 0 ? "success" : "danger"}>{humanize(m.type)}</Badge> },
              { header: "Change", align: "right", cell: (m) => `${m.quantity > 0 ? "+" : ""}${qty(m.quantity)} ${m.unitCode}` },
              { header: "Before → after", align: "right", cell: (m) => (m.bucket === "ON_HAND" ? `${qty(m.quantityBefore)} → ${qty(m.quantityAfter)}` : humanize(m.bucket ?? "")), hideOnMobile: true },
              { header: "Warehouse", cell: (m) => m.warehouseName },
              { header: "Reason", cell: (m) => m.reason ?? "—", hideOnMobile: true },
              { header: "By", cell: (m) => m.actorName ?? "—", hideOnMobile: true },
              { header: "When", cell: (m) => dateTime(m.createdAt) },
            ]}
          />
          <Pagination page={rows.data.page} totalPages={rows.data.totalPages} totalElements={rows.data.totalElements} onPage={setPage} />
        </>
      )}
    </>
  );
}
