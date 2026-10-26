"use client";

import { Ban, Check, FileText, Pencil, Plus, Send, Trash2, X } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardBody, CardHeader, DescriptionList } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable, Pagination } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { ReceivePreview, TradeInvoice, TradeInvoiceSummary } from "@/lib/api/types";
import { date, dateTime, humanize, money, qty } from "@/lib/format";
import { useApi, useDashboard, useDebounced } from "../context";
import { PageHeader, SearchBox } from "../parts";
import { TradeReturnsCard } from "./invoice-extras";

type Direction = "SALES" | "PURCHASES";

export function InvoicesScreen() {
  const { client, href, can } = useDashboard();
  const params = useSearchParams();
  const directions: { key: Direction; label: string }[] = [
    ...(can("SALE_RECORD") ? [{ key: "SALES" as const, label: "Invoices to businesses" }] : []),
    ...(can("PURCHASE_MANAGE") ? [{ key: "PURCHASES" as const, label: "Invoices from suppliers" }] : []),
  ];
  const initial = (params.get("direction") as Direction) ?? directions[0]?.key ?? "SALES";
  const [direction, setDirection] = useState<Direction>(directions.some((d) => d.key === initial) ? initial : directions[0]?.key ?? "SALES");
  const [status, setStatus] = useState(params.get("status") ?? "");
  const [q, setQ] = useState("");
  const dq = useDebounced(q);
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [direction, status, dq]);
  const list = useApi(() => api.trade.list(client, { direction, status, q: dq, page, size: 20 }), [client, direction, status, dq, page]);

  return (
    <>
      <PageHeader
        title="Business invoices"
        description="Invoice connected businesses. When the buyer approves, a sale is recorded for the seller and a purchase for the buyer, in one step."
        actions={can("SALE_RECORD") && <ButtonLink href={href("/invoices/new")} icon={<Plus className="size-4" aria-hidden />}>New invoice</ButtonLink>}
      />
      <Card>
        <div className="px-4 pt-2">
          <Tabs label="Invoice direction" value={direction} onChange={setDirection} tabs={directions} />
        </div>
        <div className="flex flex-col gap-3 border-b border-line p-4 sm:flex-row">
          <SearchBox value={q} onChange={setQ} placeholder="Invoice number or business" label="Search invoices" />
          <label htmlFor="inv-status" className="sr-only">Status</label>
          <Select id="inv-status" value={status} onChange={(e) => setStatus(e.target.value)} className="sm:ml-auto sm:w-48">
            <option value="">All statuses</option>
            {direction === "SALES" && <option value="DRAFT">Draft</option>}
            <option value="PENDING_APPROVAL">Pending approval</option>
            <option value="POSTED">Posted</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
          </Select>
        </div>
        {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : !list.data ? <LoadingBlock rows={5} /> : list.data.content.length === 0 ? (
          <EmptyState icon={FileText} title="No invoices here yet" description={direction === "SALES" ? "Create an invoice for a connected business." : "Invoices sent to you by connected suppliers appear here for approval."} />
        ) : (
          <>
            <DataTable<TradeInvoiceSummary>
              caption="B2B invoices"
              rows={list.data.content}
              rowKey={(r) => r.id}
              rowHref={(r) => href(`/invoices/${r.id}`)}
              columns={[
                { header: "Invoice", cell: (r) => r.invoiceNumber },
                { header: direction === "SALES" ? "Buyer" : "Seller", cell: (r) => r.counterparty.name },
                { header: "Date", cell: (r) => date(r.invoiceDate ?? r.createdAt) },
                { header: "Lines", align: "right", cell: (r) => r.lineCount, hideOnMobile: true },
                { header: "Total", align: "right", cell: (r) => money(r.total, r.currencyCode) },
                { header: "Status", cell: (r) => <StatusChip status={r.status} /> },
              ]}
            />
            <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  );
}

export function InvoiceDetailScreen({ id }: { id: number }) {
  const { client, href, can } = useDashboard();
  const toast = useToast();
  const router = useRouter();
  const inv = useApi(() => api.trade.get(client, id), [client, id]);
  const [reasonFor, setReasonFor] = useState<"cancel" | "reject" | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const i = inv.data;
  if (inv.error) return <Card><ErrorState error={inv.error} onRetry={inv.reload} /></Card>;
  if (!i) return <Card><LoadingBlock rows={8} /></Card>;
  const demo = client.mode === "demo" ? " (demo — not saved)" : "";
  const seller = i.role === "SELLER";
  const other = seller ? i.buyer : i.seller;

  const act = async (fn: () => Promise<TradeInvoice | void>, msg: string) => {
    setBusy(true);
    try {
      const res = await fn();
      if (res) inv.setData(res);
      toast(msg + demo);
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        back={{ href: href(seller ? "/invoices" : "/invoices?direction=PURCHASES"), label: "Business invoices" }}
        title={`${seller ? "Invoice to" : "Invoice from"} ${other.name}`}
        description={`${i.status === "DRAFT" ? "Draft" : i.invoiceNumber} · ${date(i.invoiceDate ?? i.createdAt)}`}
        actions={
          <>
            {seller && i.status === "DRAFT" && can("SALE_RECORD") && (
              <>
                <Button loading={busy} icon={<Send className="size-4" aria-hidden />} onClick={() => act(() => api.trade.send(client, i.id), "Invoice sent; your stock is reserved")}>Send to buyer</Button>
                <ButtonLink href={href(`/invoices/${i.id}/edit`)} variant="secondary" icon={<Pencil className="size-4" aria-hidden />}>Edit draft</ButtonLink>
                <Button variant="secondary" loading={busy} icon={<Trash2 className="size-4" aria-hidden />} onClick={() => act(async () => { await api.trade.remove(client, i.id); router.push(href("/invoices")); }, "Draft deleted")}>Delete draft</Button>
              </>
            )}
            {seller && i.status === "PENDING_APPROVAL" && can("SALE_RECORD") && <Button variant="secondary" icon={<Ban className="size-4" aria-hidden />} onClick={() => setReasonFor("cancel")}>Cancel invoice</Button>}
            {!seller && i.status === "PENDING_APPROVAL" && can("PURCHASE_MANAGE") && (
              <>
                <Button icon={<Check className="size-4" aria-hidden />} onClick={() => setApproveOpen(true)}>Review and approve</Button>
                <Button variant="secondary" icon={<X className="size-4" aria-hidden />} onClick={() => setReasonFor("reject")}>Reject</Button>
              </>
            )}
          </>
        }
      />
      {i.status === "PENDING_APPROVAL" && (
        <Notice tone="info" className="mb-4">
          {seller ? "Waiting for the buyer to approve. Your stock for these lines is reserved: it stays on hand but can't be sold elsewhere." : "Approving records a purchase at these prices and adds the stock to your chosen warehouse. Nothing changes until you approve."}
        </Notice>
      )}
      {i.status === "REJECTED" && <Notice tone="danger" className="mb-4" title="Rejected by the buyer">{i.rejectionReason}</Notice>}
      {i.status === "CANCELLED" && <Notice tone="warning" className="mb-4" title="Cancelled by the seller">{i.cancelReason} The reservation was released; no stock moved.</Notice>}
      {i.status === "POSTED" && <Notice tone="success" className="mb-4">Posted. {seller ? "A sale was recorded in your Sales." : "A purchase was recorded in your Purchases."} Payments are tracked there.</Notice>}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Items" action={<StatusChip status={i.status} />} />
          <DataTable
            caption="Invoice lines"
            rows={i.lines}
            rowKey={(l) => l.id}
            columns={[
              { header: "Product", cell: (l) => l.productName + (!l.defaultVariant && l.variantName ? ` · ${l.variantName}` : "") },
              { header: "Qty", align: "right", cell: (l) => `${qty(l.quantity, l.unitDecimals)} ${l.unitCode}` },
              { header: "Price", align: "right", cell: (l) => money(l.unitPrice, i.currencyCode) },
              { header: "Tax", align: "right", cell: (l) => `${l.taxRate}%`, hideOnMobile: true },
              { header: "Total", align: "right", cell: (l) => money(l.lineTotal, i.currencyCode) },
            ]}
          />
          <CardBody className="border-t border-line">
            <dl className="ml-auto max-w-xs space-y-1.5 text-sm">
              <div className="flex justify-between"><dt className="text-muted">Subtotal</dt><dd className="tabular">{money(i.subtotal, i.currencyCode)}</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Discounts</dt><dd className="tabular">−{money(i.discountTotal, i.currencyCode)}</dd></div>
              <div className="flex justify-between"><dt className="text-muted">Tax</dt><dd className="tabular">{money(i.taxTotal, i.currencyCode)}</dd></div>
              <div className="flex justify-between border-t border-line pt-2 text-base font-semibold"><dt>Total</dt><dd className="tabular">{money(i.total, i.currencyCode)}</dd></div>
            </dl>
          </CardBody>
        </Card>
        <div className="space-y-6">
          <Card>
            <CardHeader title={seller ? "Buyer" : "Seller"} />
            <CardBody>
              <DescriptionList className="sm:grid-cols-1" items={[{ label: "Business", value: other.name }, { label: "Business code", value: other.businessCode }, { label: "City", value: other.city }, { label: "Phone", value: other.phone }, { label: "Tax ID", value: other.taxId }]} />
            </CardBody>
          </Card>
          <TradeReturnsCard invoice={i} onChange={(next) => inv.setData(next)} />
          {(i.notes || i.deliveryNotes) && <Card><CardHeader title="Notes" /><CardBody className="space-y-2 whitespace-pre-line text-sm">{i.notes && <p>{i.notes}</p>}{i.deliveryNotes && <p className="text-muted">Delivery: {i.deliveryNotes}</p>}</CardBody></Card>}
          <Card>
            <CardHeader title="Timeline" />
            <ol className="space-y-3 px-5 py-4">
              {i.events.map((e, k) => (
                <li key={k} className="text-sm">
                  <p className="font-medium">{humanize(e.action)}</p>
                  <p className="text-xs text-muted">{e.businessName}{e.actorName ? ` · ${e.actorName}` : ""} · {dateTime(e.at)}</p>
                  {e.note && <p className="text-xs text-ink-2">{e.note}</p>}
                </li>
              ))}
            </ol>
          </Card>
        </div>
      </div>

      <ReasonDialog
        open={reasonFor !== null}
        title={reasonFor === "cancel" ? "Cancel this invoice?" : "Reject this invoice?"}
        description={reasonFor === "cancel" ? "The reserved stock is released. The buyer is notified." : "The seller's reservation is released and no stock moves."}
        onClose={() => setReasonFor(null)}
        onSubmit={async (reason) => {
          const res = reasonFor === "cancel" ? await api.trade.cancel(client, i.id, reason) : await api.trade.reject(client, i.id, reason);
          inv.setData(res);
          toast((reasonFor === "cancel" ? "Invoice cancelled" : "Invoice rejected") + demo);
        }}
      />
      <ApproveDialog open={approveOpen} invoice={i} onClose={() => setApproveOpen(false)} onApproved={(res) => { inv.setData(res); toast("Invoice approved; stock received" + demo); }} />
    </>
  );
}

function ReasonDialog({ open, title, description, onClose, onSubmit }: { open: boolean; title: string; description: string; onClose: () => void; onSubmit: (reason: string) => Promise<void> }) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (open) { setReason(""); setError(undefined); } }, [open]);
  return (
    <Dialog open={open} onClose={onClose} title={title} description={description} footer={<><Button variant="secondary" onClick={onClose}>Back</Button><Button variant="danger" loading={saving} onClick={async () => {
      if (!reason.trim()) return setError("A reason is required");
      setSaving(true);
      try { await onSubmit(reason.trim()); onClose(); } catch (e) { setError((e as Error).message); } finally { setSaving(false); }
    }}>Confirm</Button></>}>
      <Field label="Reason" required error={error}>{(p) => <Input {...p} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} />}</Field>
    </Dialog>
  );
}

function ApproveDialog({ open, invoice, onClose, onApproved }: { open: boolean; invoice: TradeInvoice; onClose: () => void; onApproved: (i: TradeInvoice) => void }) {
  const { client } = useDashboard();
  const warehouses = useApi(() => api.warehouses(client), [client]);
  const [warehouseId, setWarehouseId] = useState<number>();
  const [preview, setPreview] = useState<ReceivePreview>();
  const [choices, setChoices] = useState<Record<number, string>>({});
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setError(undefined);
    api.trade.preview(client, invoice.id, warehouseId).then((p) => {
      setPreview(p);
      setWarehouseId((w) => w ?? p.warehouseId);
      setChoices(Object.fromEntries(p.lines.map((l) => [l.lineId, l.target ? String(l.target.variantId) : l.mode === "NEW" ? "new" : ""])));
    }).catch((e) => setError(e.message));
  }, [open, client, invoice.id, warehouseId]);

  const approve = async () => {
    if (!preview) return;
    const missing = preview.lines.filter((l) => !choices[l.lineId]);
    if (missing.length) return setError("Choose where each highlighted line should go.");
    setSaving(true);
    setError(undefined);
    try {
      const res = await api.trade.approve(client, invoice.id, {
        warehouseId,
        lines: preview.lines.map((l) => (choices[l.lineId] === "new" ? { lineId: l.lineId, createNew: true } : { lineId: l.lineId, variantId: Number(choices[l.lineId]), createNew: false })),
      });
      onApproved(res);
      onClose();
    } catch (e) {
      setError(e instanceof ApiError && e.code === "NEEDS_REVIEW" ? "Several of your products match a line. Choose one for each highlighted line." : (e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const modeLabel = { MAPPED: "Linked product", MATCHED: "Matched", NEW: "New product will be created", REVIEW: "Choose a product" };
  return (
    <Dialog open={open} onClose={onClose} size="lg" title="Review and approve" description="Check where each line goes in your stock. Units are never converted." footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={saving} onClick={approve} disabled={!preview}>Approve and receive stock</Button></>}>
      <div className="space-y-4">
        {error && <Notice tone="danger">{error}</Notice>}
        <Field label="Receive into warehouse">
          {(p) => <Select {...p} value={warehouseId ?? ""} onChange={(e) => setWarehouseId(Number(e.target.value))}>{(warehouses.data ?? []).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</Select>}
        </Field>
        {!preview ? <LoadingBlock rows={3} /> : (
          <ul className="space-y-3">
            {preview.lines.map((l) => {
              const line = invoice.lines.find((x) => x.id === l.lineId);
              return (
                <li key={l.lineId} className={`rounded-lg border p-3 ${l.mode === "REVIEW" && !choices[l.lineId] ? "border-warning bg-warning-bg/40" : "border-line"}`}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-medium">{line?.productName} · {qty(line?.quantity)} {line?.unitCode}</p>
                    <span className="text-xs text-muted">{modeLabel[l.mode]}</span>
                  </div>
                  <label className="mt-2 block text-xs font-medium text-ink-2" htmlFor={`line-${l.lineId}`}>Receive into</label>
                  <Select id={`line-${l.lineId}`} className="mt-1" value={choices[l.lineId] ?? ""} onChange={(e) => setChoices((c) => ({ ...c, [l.lineId]: e.target.value }))}>
                    <option value="">Choose…</option>
                    {(l.candidates ?? (l.target ? [l.target] : [])).map((c) => <option key={c.variantId} value={c.variantId}>{c.productName}{c.variantName && c.variantName !== c.productName ? ` · ${c.variantName}` : ""} ({qty(c.available)} {c.unitCode} now)</option>)}
                    <option value="new">Create a new product</option>
                  </Select>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Dialog>
  );
}
