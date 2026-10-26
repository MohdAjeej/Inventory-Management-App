"use client";

import { Ban, CreditCard, Plus, Printer, ShoppingCart, Trash2, Truck, Undo2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { DataTable, Pagination } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { ApiError, newRequestId } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import { PAYMENT_METHODS, type Payment, type PurchaseSummary, type SaleSummary, type Warehouse } from "@/lib/api/types";
import { date, dateTime, humanize, money, qty } from "@/lib/format";
import { useApi, useDashboard, useDebounced } from "../context";
import { PageHeader, ProductPicker, SearchBox, type PickedVariant } from "../parts";

type Kind = "sale" | "purchase";

// ------------------------------------------------------------------ lists

function useListFilters() {
  const [q, setQ] = useState("");
  const dq = useDebounced(q);
  const [paymentStatus, setPaymentStatus] = useState("");
  const [status, setStatus] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [dq, paymentStatus, status, from, to]);
  return { q, setQ, dq, paymentStatus, setPaymentStatus, status, setStatus, from, setFrom, to, setTo, page, setPage };
}

function ListFilters({ f, kind }: { f: ReturnType<typeof useListFilters>; kind: Kind }) {
  return (
    <div className="flex flex-col gap-3 border-b border-line p-4 xl:flex-row xl:items-center">
      <SearchBox value={f.q} onChange={f.setQ} placeholder={kind === "sale" ? "Invoice no., customer name or phone" : "Purchase no., bill ref. or supplier"} label={`Search ${kind}s`} />
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 xl:ml-auto xl:flex">
        <label className="sr-only" htmlFor={`${kind}-pay`}>Payment status</label>
        <Select id={`${kind}-pay`} value={f.paymentStatus} onChange={(e) => f.setPaymentStatus(e.target.value)} className="xl:w-36">
          <option value="">Any payment</option>
          <option value="PAID">Paid</option>
          <option value="PARTIALLY_PAID">Partly paid</option>
          <option value="UNPAID">Unpaid</option>
        </Select>
        <label className="sr-only" htmlFor={`${kind}-status`}>Status</label>
        <Select id={`${kind}-status`} value={f.status} onChange={(e) => f.setStatus(e.target.value)} className="xl:w-36">
          <option value="">Any status</option>
          <option value={kind === "sale" ? "COMPLETED" : "RECEIVED"}>{kind === "sale" ? "Completed" : "Received"}</option>
          <option value="CANCELLED">Cancelled</option>
        </Select>
        <label className="sr-only" htmlFor={`${kind}-from`}>From date</label>
        <Input id={`${kind}-from`} type="date" value={f.from} onChange={(e) => f.setFrom(e.target.value)} className="xl:w-40" title="From date" />
        <label className="sr-only" htmlFor={`${kind}-to`}>To date</label>
        <Input id={`${kind}-to`} type="date" value={f.to} onChange={(e) => f.setTo(e.target.value)} className="xl:w-40" title="To date" />
      </div>
    </div>
  );
}

export function SalesScreen() {
  const { client, href, can, currency } = useDashboard();
  const f = useListFilters();
  const list = useApi(() => api.sales.list(client, { q: f.dq, paymentStatus: f.paymentStatus, status: f.status, from: f.from, to: f.to, page: f.page, size: 20 }), [client, f.dq, f.paymentStatus, f.status, f.from, f.to, f.page]);
  return (
    <>
      <PageHeader title="Sales" description="Every sale reduces stock when it is saved." actions={can("SALE_RECORD") && <ButtonLink href={href("/sales/new")} icon={<ShoppingCart className="size-4" aria-hidden />}>Record sale</ButtonLink>} />
      <Card>
        <ListFilters f={f} kind="sale" />
        {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : !list.data ? <LoadingBlock rows={6} /> : list.data.content.length === 0 ? (
          <EmptyState icon={ShoppingCart} title="No sales found" description={f.dq || f.paymentStatus || f.status || f.from ? "Try clearing a filter." : "Recorded sales will appear here."} />
        ) : (
          <>
            <DataTable<SaleSummary>
              caption="Sales"
              rows={list.data.content}
              rowKey={(r) => r.id}
              rowHref={(r) => href(`/sales/${r.id}`)}
              columns={[
                { header: "Invoice", cell: (r) => r.invoiceNumber },
                { header: "Date", cell: (r) => date(r.soldAt) },
                { header: "Customer", cell: (r) => r.customerName ?? "Walk-in" },
                { header: "Products", cell: (r) => <span className="line-clamp-1 max-w-56">{r.products}</span>, hideOnMobile: true },
                { header: "Total", align: "right", cell: (r) => money(r.total, currency) },
                { header: "Outstanding", align: "right", cell: (r) => money(r.outstanding, currency), hideOnMobile: true },
                { header: "Payment", cell: (r) => (r.status === "CANCELLED" ? <StatusChip status="CANCELLED" /> : <StatusChip status={r.paymentStatus} />) },
              ]}
            />
            <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={f.setPage} />
          </>
        )}
      </Card>
    </>
  );
}

export function PurchasesScreen() {
  const { client, href, can, currency } = useDashboard();
  const f = useListFilters();
  const list = useApi(() => api.purchases.list(client, { q: f.dq, paymentStatus: f.paymentStatus, status: f.status, from: f.from, to: f.to, page: f.page, size: 20 }), [client, f.dq, f.paymentStatus, f.status, f.from, f.to, f.page]);
  return (
    <>
      <PageHeader title="Purchases" description="Purchases add stock and update each product's average cost." actions={can("PURCHASE_MANAGE") && <ButtonLink href={href("/purchases/new")} icon={<Truck className="size-4" aria-hidden />}>Record purchase</ButtonLink>} />
      <Card>
        <ListFilters f={f} kind="purchase" />
        {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : !list.data ? <LoadingBlock rows={6} /> : list.data.content.length === 0 ? (
          <EmptyState icon={Truck} title="No purchases found" />
        ) : (
          <>
            <DataTable<PurchaseSummary>
              caption="Purchases"
              rows={list.data.content}
              rowKey={(r) => r.id}
              rowHref={(r) => href(`/purchases/${r.id}`)}
              columns={[
                { header: "Purchase", cell: (r) => r.purchaseNumber },
                { header: "Date", cell: (r) => date(r.purchasedAt) },
                { header: "Supplier", cell: (r) => r.supplierName ?? "—" },
                { header: "Bill ref.", cell: (r) => r.referenceNumber ?? "—", hideOnMobile: true },
                { header: "Total", align: "right", cell: (r) => money(r.total, currency) },
                { header: "Outstanding", align: "right", cell: (r) => money(r.outstanding, currency), hideOnMobile: true },
                { header: "Payment", cell: (r) => (r.status === "CANCELLED" ? <StatusChip status="CANCELLED" /> : <StatusChip status={r.paymentStatus} />) },
              ]}
            />
            <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={f.setPage} />
          </>
        )}
      </Card>
    </>
  );
}

// ------------------------------------------------------------------ detail

function PaymentsCard({ payments, currency }: { payments: Payment[]; currency: string }) {
  return (
    <Card>
      <CardHeader title="Payments" />
      {payments.length === 0 ? (
        <EmptyState title="No payments yet" description="This isn't marked as paid until a payment is recorded." />
      ) : (
        <ul className="divide-y divide-line">
          {payments.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-3 px-5 py-3 text-sm">
              <div>
                <p className="font-medium">{p.kind === "REFUND" ? "Refund" : "Payment"} · {humanize(p.method)}</p>
                <p className="text-xs text-muted">{dateTime(p.paidAt)}{p.recordedBy ? ` · ${p.recordedBy}` : ""}{p.reference ? ` · Ref ${p.reference}` : ""}</p>
              </div>
              <span className={`font-semibold tabular ${p.kind === "REFUND" ? "text-danger" : ""}`}>{p.kind === "REFUND" ? "−" : ""}{money(p.amount, currency)}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function PaymentDialog({ open, onClose, outstanding, currency, onSubmit }: { open: boolean; onClose: () => void; outstanding: number; currency: string; onSubmit: (b: { amount: number; method: string; reference?: string; clientRequestId: string }) => Promise<void> }) {
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState("CASH");
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [rid, setRid] = useState(newRequestId);
  useEffect(() => {
    if (open) {
      setAmount(String(outstanding));
      setError(undefined);
      setRid(newRequestId());
    }
  }, [open, outstanding]);
  const submit = async () => {
    const a = Number(amount);
    if (!(a > 0)) return setError("Enter an amount greater than zero");
    if (a > outstanding + 0.001) return setError(`Amount can't exceed the outstanding ${money(outstanding, currency)}`);
    setSaving(true);
    setError(undefined);
    try {
      await onSubmit({ amount: a, method, reference: reference || undefined, clientRequestId: rid });
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={open} onClose={onClose} title="Record payment" description={`Outstanding: ${money(outstanding, currency)}`} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={saving}>Save payment</Button></>}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Amount" required error={error}>{(p) => <Input {...p} type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />}</Field>
        <Field label="Method">{(p) => <Select {...p} value={method} onChange={(e) => setMethod(e.target.value)}>{PAYMENT_METHODS.map((m) => <option key={m} value={m}>{humanize(m)}</option>)}</Select>}</Field>
        <Field label="Reference" className="sm:col-span-2" hint="UPI ref., cheque no. etc.">{(p) => <Input {...p} value={reference} onChange={(e) => setReference(e.target.value)} maxLength={100} />}</Field>
      </div>
    </Dialog>
  );
}

function CancelDialog({ open, onClose, title, description, withRefund, onSubmit }: { open: boolean; onClose: () => void; title: string; description: string; withRefund?: boolean; onSubmit: (reason: string, refundMethod: string) => Promise<void> }) {
  const [reason, setReason] = useState("");
  const [refund, setRefund] = useState("CASH");
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    if (open) {
      setReason("");
      setError(undefined);
    }
  }, [open]);
  const submit = async () => {
    if (!reason.trim()) return setError("A reason is required");
    setSaving(true);
    try {
      await onSubmit(reason.trim(), refund);
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={open} onClose={onClose} title={title} description={description} footer={<><Button variant="secondary" onClick={onClose}>Keep it</Button><Button variant="danger" onClick={submit} loading={saving}>Confirm cancellation</Button></>}>
      <div className="space-y-4">
        <Field label="Reason" required error={error}>{(p) => <Input {...p} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} />}</Field>
        {withRefund && <Field label="Refund method for money received">{(p) => <Select {...p} value={refund} onChange={(e) => setRefund(e.target.value)}>{PAYMENT_METHODS.map((m) => <option key={m} value={m}>{humanize(m)}</option>)}</Select>}</Field>}
      </div>
    </Dialog>
  );
}

function Totals({ rows }: { rows: [string, string, boolean?][] }) {
  return (
    <dl className="space-y-1.5 text-sm">
      {rows.map(([label, value, strong]) => (
        <div key={label} className={`flex justify-between gap-4 ${strong ? "border-t border-line pt-2 text-base font-semibold" : ""}`}>
          <dt className={strong ? "" : "text-muted"}>{label}</dt>
          <dd className="tabular">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function SaleDetailScreen({ id }: { id: number }) {
  const { client, href, can, currency } = useDashboard();
  const toast = useToast();
  const sale = useApi(() => api.sales.get(client, id), [client, id]);
  const [payOpen, setPayOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const s = sale.data;
  if (sale.error) return <Card><ErrorState error={sale.error} onRetry={sale.reload} /></Card>;
  if (!s) return <Card><LoadingBlock rows={8} /></Card>;
  const demo = client.mode === "demo" ? " (demo — not saved)" : "";
  return (
    <>
      <PageHeader
        back={{ href: href("/sales"), label: "Sales" }}
        title={`Sale ${s.invoiceNumber}`}
        description={`${dateTime(s.soldAt)} · ${s.customerName ?? "Walk-in customer"}${s.soldBy ? ` · by ${s.soldBy}` : ""}`}
        actions={
          s.status === "COMPLETED" && (
            <>
              {can("SALE_RECORD") && s.outstanding > 0 && <Button icon={<CreditCard className="size-4" aria-hidden />} onClick={() => setPayOpen(true)}>Record payment</Button>}
              {can("SALE_CANCEL") && <ButtonLink href={href(`/sales/${s.id}/return`)} variant="secondary" icon={<Undo2 className="size-4" aria-hidden />}>Record return</ButtonLink>}
              {can("SALE_CANCEL") && <Button variant="secondary" icon={<Ban className="size-4" aria-hidden />} onClick={() => setCancelOpen(true)}>Cancel sale</Button>}
              <ButtonLink href={href(`/sales/${s.id}/receipt`)} variant="ghost" icon={<Printer className="size-4" aria-hidden />}>Receipt</ButtonLink>
            </>
          )
        }
      />
      {s.status === "CANCELLED" && <Notice tone="warning" className="mb-4" title="This sale was cancelled">{s.cancelReason} · {dateTime(s.cancelledAt)}{s.cancelledBy ? ` by ${s.cancelledBy}` : ""}. Unreturned stock was put back and money received was refunded.</Notice>}
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader title="Items" action={<div className="flex gap-2"><StatusChip status={s.status} />{s.status === "COMPLETED" && <StatusChip status={s.paymentStatus} />}</div>} />
            <DataTable
              caption="Sale items"
              rows={s.items}
              rowKey={(i) => i.id}
              columns={[
                { header: "Product", cell: (i) => i.productName + (i.variantName && i.variantName !== i.productName ? ` · ${i.variantName}` : "") },
                { header: "Qty", align: "right", cell: (i) => `${qty(i.quantity)} ${i.unitCode}${i.returnedQuantity ? ` (${qty(i.returnedQuantity)} returned)` : ""}` },
                { header: "Price", align: "right", cell: (i) => money(i.unitPrice, currency) },
                { header: "Discount", align: "right", cell: (i) => money(i.discount, currency), hideOnMobile: true },
                { header: "Tax", align: "right", cell: (i) => `${i.taxRate}%`, hideOnMobile: true },
                { header: "Total", align: "right", cell: (i) => money(i.lineTotal, currency) },
              ]}
            />
            <CardBody className="border-t border-line">
              <div className="ml-auto max-w-xs">
                <Totals rows={[["Subtotal", money(s.subtotal, currency)], ["Discounts", `−${money(s.discountTotal, currency)}`], ["Tax", money(s.taxTotal, currency)], ...(s.returnedTotal ? [["Returned", `−${money(s.returnedTotal, currency)}`] as [string, string]] : []), ["Total", money(s.total, currency), true], ["Paid", money(s.amountPaid, currency)], ["Outstanding", money(s.outstanding, currency)]]} />
              </div>
            </CardBody>
          </Card>
          {s.returns.length > 0 && (
            <Card>
              <CardHeader title="Returns" />
              <ul className="divide-y divide-line">
                {s.returns.map((r) => (
                  <li key={r.id} className="px-5 py-3 text-sm">
                    <p className="font-medium">{r.returnNumber} · {date(r.returnedAt)} · credit {money(r.creditAmount, currency)}</p>
                    <p className="text-muted">{r.reason} · {r.items.map((i) => `${qty(i.quantity)} × ${i.productName}${i.resalable ? "" : " (damaged)"}`).join(", ")}</p>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
        <div className="space-y-6">
          {s.grossProfit !== undefined && (
            <Card>
              <CardHeader title="Profit" description="Uses the cost saved at the time of sale." />
              <CardBody><Totals rows={[["Net sales", money(s.netSales, currency)], ["Cost of goods", money(s.costTotal, currency)], ["Gross profit", money(s.grossProfit, currency), true]]} /></CardBody>
            </Card>
          )}
          <PaymentsCard payments={s.payments} currency={currency} />
          {s.notes && <Card><CardHeader title="Notes" /><CardBody className="whitespace-pre-line text-sm">{s.notes}</CardBody></Card>}
        </div>
      </div>
      <PaymentDialog open={payOpen} onClose={() => setPayOpen(false)} outstanding={s.outstanding} currency={currency} onSubmit={async (b) => { sale.setData(await api.sales.addPayment(client, s.id, b)); toast("Payment recorded" + demo); }} />
      <CancelDialog open={cancelOpen} onClose={() => setCancelOpen(false)} withRefund={s.amountPaid > 0} title="Cancel this sale?" description="All unreturned stock goes back, and money received is refunded. The record is kept." onSubmit={async (reason, refundMethod) => { sale.setData(await api.sales.cancel(client, s.id, { reason, refundMethod })); toast("Sale cancelled" + demo); }} />
    </>
  );
}

export function PurchaseDetailScreen({ id }: { id: number }) {
  const { client, href, can, currency } = useDashboard();
  const toast = useToast();
  const purchase = useApi(() => api.purchases.get(client, id), [client, id]);
  const [payOpen, setPayOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const p = purchase.data;
  if (purchase.error) return <Card><ErrorState error={purchase.error} onRetry={purchase.reload} /></Card>;
  if (!p) return <Card><LoadingBlock rows={8} /></Card>;
  const demo = client.mode === "demo" ? " (demo — not saved)" : "";
  return (
    <>
      <PageHeader
        back={{ href: href("/purchases"), label: "Purchases" }}
        title={`Purchase ${p.purchaseNumber}`}
        description={`${dateTime(p.purchasedAt)} · ${p.supplierName ?? "No supplier"}${p.referenceNumber ? ` · Bill ${p.referenceNumber}` : ""}`}
        actions={p.status === "RECEIVED" && can("PURCHASE_MANAGE") && (
          <>
            {p.outstanding > 0 && <Button icon={<CreditCard className="size-4" aria-hidden />} onClick={() => setPayOpen(true)}>Record payment</Button>}
            <Button variant="secondary" icon={<Ban className="size-4" aria-hidden />} onClick={() => setCancelOpen(true)}>Cancel purchase</Button>
          </>
        )}
      />
      {p.status === "CANCELLED" && <Notice tone="warning" className="mb-4" title="This purchase was cancelled">{p.cancelReason} · {dateTime(p.cancelledAt)}. The stock was removed again.</Notice>}
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Items received" action={<div className="flex gap-2"><StatusChip status={p.status} />{p.status === "RECEIVED" && <StatusChip status={p.paymentStatus} />}</div>} />
          <DataTable
            caption="Purchase items"
            rows={p.items}
            rowKey={(i) => i.id}
            columns={[
              { header: "Product", cell: (i) => i.productName },
              { header: "Qty", align: "right", cell: (i) => `${qty(i.quantity)} ${i.unitCode}` },
              { header: "Unit cost", align: "right", cell: (i) => money(i.unitCost, currency) },
              { header: "Warehouse", cell: (i) => i.warehouseName ?? "—", hideOnMobile: true },
              { header: "Total", align: "right", cell: (i) => money(i.lineTotal, currency) },
            ]}
          />
          <CardBody className="border-t border-line">
            <div className="ml-auto max-w-xs">
              <Totals rows={[["Subtotal", money(p.subtotal, currency)], ["Discounts", `−${money(p.discountTotal, currency)}`], ["Tax", money(p.taxTotal, currency)], ["Total", money(p.total, currency), true], ["Paid", money(p.amountPaid, currency)], ["Outstanding", money(p.outstanding, currency)]]} />
            </div>
          </CardBody>
        </Card>
        <div className="space-y-6">
          <PaymentsCard payments={p.payments} currency={currency} />
          {p.notes && <Card><CardHeader title="Notes" /><CardBody className="whitespace-pre-line text-sm">{p.notes}</CardBody></Card>}
        </div>
      </div>
      <PaymentDialog open={payOpen} onClose={() => setPayOpen(false)} outstanding={p.outstanding} currency={currency} onSubmit={async (b) => { purchase.setData(await api.purchases.addPayment(client, p.id, b)); toast("Payment recorded" + demo); }} />
      <CancelDialog open={cancelOpen} onClose={() => setCancelOpen(false)} title="Cancel this purchase?" description="The received stock is removed again. This is refused if some of it has already been sold." onSubmit={async (reason) => { purchase.setData(await api.purchases.cancel(client, p.id, { reason })); toast("Purchase cancelled" + demo); }} />
    </>
  );
}

// ------------------------------------------------------------------ forms

interface Line {
  key: string;
  picked: PickedVariant;
  quantity: string;
  price: string;
  discount: string;
  taxRate: string;
  batchId: string;
  lotNumber: string;
  expiryDate: string;
  serials: string;
}

const serialsOf = (l: Line) => l.serials.split(/[\n,]/).map((x) => x.trim()).filter(Boolean);

/** Batch / lot / expiry / serial inputs for products that track them. */
function LineTracking({ line, kind, errors, index, onChange }: { line: Line; kind: Kind; errors: Record<string, string>; index: number; onChange: (patch: Partial<Line>) => void }) {
  const { client } = useDashboard();
  const { trackBatches, trackSerials } = line.picked.product;
  const batches = useApi(() => (kind === "sale" && trackBatches ? api.productAdmin.batches(client, line.picked.variant.id) : Promise.resolve([])), [client, line.picked.variant.id, kind, trackBatches]);
  if (!trackBatches && !trackSerials) return null;
  return (
    <div className="mt-3 grid gap-3 border-t border-dashed border-line pt-3 sm:grid-cols-2">
      {trackBatches && kind === "sale" && (
        <Field label="Batch" required error={errors[`items[${index}].batchId`]}>
          {(p) => (
            <Select {...p} value={line.batchId} onChange={(e) => onChange({ batchId: e.target.value })}>
              <option value="">Choose…</option>
              {(batches.data ?? []).filter((b) => b.onHand > 0).map((b) => <option key={b.id} value={b.id}>{b.lotNumber ?? `Batch ${b.id}`}{b.expiryDate ? ` · exp. ${b.expiryDate}` : ""} · {qty(b.onHand)} left</option>)}
            </Select>
          )}
        </Field>
      )}
      {trackBatches && kind === "purchase" && (
        <>
          <Field label="Lot number" required error={errors[`items[${index}].lotNumber`]}>{(p) => <Input {...p} value={line.lotNumber} onChange={(e) => onChange({ lotNumber: e.target.value })} maxLength={80} />}</Field>
          <Field label="Expiry date">{(p) => <Input {...p} type="date" value={line.expiryDate} onChange={(e) => onChange({ expiryDate: e.target.value })} />}</Field>
        </>
      )}
      {trackSerials && (
        <Field label="Serial numbers" required className="sm:col-span-2" hint="One per line or comma-separated; one per unit" error={errors[`items[${index}].serials`]}>
          {(p) => <Textarea {...p} className="min-h-16 font-mono" value={line.serials} onChange={(e) => onChange({ serials: e.target.value })} />}
        </Field>
      )}
    </div>
  );
}

/** Client-side preview only. Final amounts are always calculated by the server on save. */
function estimate(lines: Line[]) {
  let subtotal = 0, discount = 0, tax = 0;
  for (const l of lines) {
    const gross = (Number(l.quantity) || 0) * (Number(l.price) || 0);
    const d = Number(l.discount) || 0;
    subtotal += gross;
    discount += d;
    tax += ((gross - d) * (Number(l.taxRate) || 0)) / 100;
  }
  return { subtotal, discount, tax, total: subtotal - discount + tax };
}

export function TransactionFormScreen({ kind }: { kind: Kind }) {
  const { client, href, can, currency } = useDashboard();
  const router = useRouter();
  const toast = useToast();
  const allowed = kind === "sale" ? can("SALE_RECORD") : can("PURCHASE_MANAGE");
  const parties = useApi(() => (allowed ? api.parties.list(client, kind === "sale" ? "customers" : "suppliers", {}) : Promise.resolve([])), [client, kind]);
  const warehouses = useApi(() => api.warehouses(client), [client]);
  const [lines, setLines] = useState<Line[]>([]);
  const [warehouseId, setWarehouseId] = useState<string>("");
  const [partyId, setPartyId] = useState("");
  const [partyName, setPartyName] = useState("");
  const [partyPhone, setPartyPhone] = useState("");
  const [saveParty, setSaveParty] = useState(true);
  const [reference, setReference] = useState("");
  const [payAmount, setPayAmount] = useState("");
  const [payMethod, setPayMethod] = useState("CASH");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [requestId] = useState(newRequestId);

  useEffect(() => {
    const def = warehouses.data?.find((w: Warehouse) => w.isDefault);
    if (def && !warehouseId) setWarehouseId(String(def.id));
  }, [warehouses.data]); // eslint-disable-line react-hooks/exhaustive-deps

  const title = kind === "sale" ? "Record sale" : "Record purchase";
  if (!allowed) return <><PageHeader title={title} /><Notice tone="warning">You don&apos;t have permission to {kind === "sale" ? "record sales" : "record purchases"}.</Notice></>;

  const est = estimate(lines);
  const update = (key: string, patch: Partial<Line>) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!lines.length) errs.items = "Add at least one product";
    lines.forEach((l, i) => {
      if (!(Number(l.quantity) > 0)) errs[`items[${i}].quantity`] = "Quantity must be greater than zero";
      if (l.price === "" || Number(l.price) < 0) errs[`items[${i}].${kind === "sale" ? "unitPrice" : "unitCost"}`] = "Enter a price";
      const gross = Number(l.quantity) * Number(l.price);
      if (Number(l.discount) > gross) errs[`items[${i}].discount`] = "Discount can't exceed the line amount";
      if (l.picked.product.trackBatches && kind === "sale" && !l.batchId) errs[`items[${i}].batchId`] = "Choose the batch";
      if (l.picked.product.trackBatches && kind === "purchase" && !l.lotNumber.trim()) errs[`items[${i}].lotNumber`] = "Enter the lot number";
      if (l.picked.product.trackSerials && serialsOf(l).length !== Number(l.quantity)) errs[`items[${i}].serials`] = `Enter exactly ${l.quantity} serial numbers`;
    });
    if (payAmount && (Number(payAmount) < 0 || Number.isNaN(Number(payAmount)))) errs["payment.amount"] = "Enter a valid amount";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    setFormError(undefined);
    const wh = warehouseId ? Number(warehouseId) : null;
    const payment = payAmount && Number(payAmount) > 0 ? { method: payMethod, amount: Number(payAmount) } : null;
    try {
      if (kind === "sale") {
        const sale = await api.sales.create(client, {
          items: lines.map((l) => ({ variantId: l.picked.variant.id, warehouseId: wh, quantity: Number(l.quantity), unitPrice: Number(l.price), discount: Number(l.discount) || 0, taxRate: l.taxRate === "" ? null : Number(l.taxRate), batchId: l.batchId ? Number(l.batchId) : null, serials: l.picked.product.trackSerials ? serialsOf(l) : null })),
          customerId: partyId ? Number(partyId) : null,
          customerName: partyId ? undefined : partyName || undefined,
          customerPhone: partyId ? undefined : partyPhone || undefined,
          saveCustomer: !partyId && !!partyName && saveParty,
          invoiceNumber: reference || undefined,
          payment,
          notes: notes || undefined,
          clientRequestId: requestId,
        });
        toast(client.mode === "demo" ? "Sale recorded (demo — not saved)" : "Sale recorded");
        router.push(href(`/sales/${sale.id}`));
      } else {
        const purchase = await api.purchases.create(client, {
          items: lines.map((l) => ({ variantId: l.picked.variant.id, warehouseId: wh, quantity: Number(l.quantity), unitCost: Number(l.price), discount: Number(l.discount) || 0, taxRate: l.taxRate === "" ? null : Number(l.taxRate), lotNumber: l.lotNumber || undefined, expiryDate: l.expiryDate || undefined, serials: l.picked.product.trackSerials ? serialsOf(l) : null })),
          supplierId: partyId ? Number(partyId) : null,
          supplierName: partyId ? undefined : partyName || undefined,
          saveSupplier: !partyId && !!partyName && saveParty,
          referenceNumber: reference || undefined,
          payment,
          notes: notes || undefined,
          clientRequestId: requestId,
        });
        toast(client.mode === "demo" ? "Purchase recorded (demo — not saved)" : "Purchase recorded");
        router.push(href(`/purchases/${purchase.id}`));
      }
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        setFormError(err.message);
      } else setFormError("Something went wrong");
      setSaving(false);
    }
  };

  const priceKey = kind === "sale" ? "unitPrice" : "unitCost";
  return (
    <>
      <PageHeader
        back={{ href: href(kind === "sale" ? "/sales" : "/purchases"), label: kind === "sale" ? "Sales" : "Purchases" }}
        title={title}
        description={kind === "sale" ? "Saving deducts stock in one step. If any line is short of stock, nothing is saved." : "Saving adds stock and updates the weighted-average cost of each product."}
      />
      <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {formError && <Notice tone="danger" title={formError.includes("stock") ? "Not enough stock" : undefined}>{formError}</Notice>}
          <Card>
            <CardHeader title="Products" description="Prices exclude tax." />
            <CardBody className="space-y-4">
              <ProductPicker
                excludeVariantIds={lines.map((l) => l.picked.variant.id)}
                onPick={(picked) =>
                  setLines((ls) => [
                    ...ls,
                    {
                      key: `${picked.variant.id}-${Date.now()}`,
                      picked,
                      quantity: "1",
                      price: String((kind === "sale" ? picked.product.salePrice : picked.product.purchasePrice ?? picked.product.averageCost) ?? ""),
                      discount: "",
                      taxRate: picked.product.taxRate !== undefined ? String(picked.product.taxRate) : "",
                      batchId: "",
                      lotNumber: "",
                      expiryDate: "",
                      serials: "",
                    },
                  ])
                }
              />
              {errors.items && <p className="text-sm font-medium text-danger">{errors.items}</p>}
              {lines.length === 0 ? (
                <p className="rounded-lg border border-dashed border-line-strong px-4 py-6 text-center text-sm text-muted">Search above to add products.</p>
              ) : (
                <ul className="space-y-3">
                  {lines.map((l, i) => {
                    const avail = l.picked.product.balances.filter((b) => b.variantId === l.picked.variant.id && (!warehouseId || b.warehouseId === Number(warehouseId))).reduce((a, b) => a + b.available, 0);
                    return (
                      <li key={l.key} className="rounded-lg border border-line p-3">
                        <div className="mb-3 flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="truncate font-medium">{l.picked.product.name}{!l.picked.variant.isDefault && ` · ${l.picked.variant.name}`}</p>
                            <p className="text-xs text-muted">{qty(avail)} {l.picked.product.baseUnit.code} available in this warehouse</p>
                          </div>
                          <button type="button" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} className="rounded-md p-1.5 text-muted hover:bg-danger-bg hover:text-danger" aria-label={`Remove ${l.picked.product.name}`}>
                            <Trash2 className="size-4" aria-hidden />
                          </button>
                        </div>
                        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                          <Field label={`Qty (${l.picked.product.baseUnit.code})`} error={errors[`items[${i}].quantity`]}>{(p) => <Input {...p} type="number" min={0} step="any" value={l.quantity} onChange={(e) => update(l.key, { quantity: e.target.value })} />}</Field>
                          <Field label={kind === "sale" ? "Selling price" : "Unit cost"} error={errors[`items[${i}].${priceKey}`]}>{(p) => <Input {...p} type="number" min={0} step="0.01" value={l.price} onChange={(e) => update(l.key, { price: e.target.value })} />}</Field>
                          <Field label="Discount" error={errors[`items[${i}].discount`]}>{(p) => <Input {...p} type="number" min={0} step="0.01" value={l.discount} onChange={(e) => update(l.key, { discount: e.target.value })} placeholder="0" />}</Field>
                          <Field label="Tax %" error={errors[`items[${i}].taxRate`]}>{(p) => <Input {...p} type="number" min={0} max={100} step="0.01" value={l.taxRate} onChange={(e) => update(l.key, { taxRate: e.target.value })} />}</Field>
                        </div>
                        <LineTracking line={l} kind={kind} index={i} errors={errors} onChange={(patch) => update(l.key, patch)} />
                      </li>
                    );
                  })}
                </ul>
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Notes" />
            <CardBody><Field label="Notes (optional)">{(p) => <Textarea {...p} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} />}</Field></CardBody>
          </Card>
        </div>
        <div className="space-y-6">
          <Card>
            <CardHeader title={kind === "sale" ? "Customer" : "Supplier"} />
            <CardBody className="space-y-4">
              <Field label={kind === "sale" ? "Saved customer" : "Saved supplier"}>
                {(p) => (
                  <Select {...p} value={partyId} onChange={(e) => setPartyId(e.target.value)}>
                    <option value="">{kind === "sale" ? "Walk-in / new customer" : "New / unnamed supplier"}</option>
                    {(parties.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </Select>
                )}
              </Field>
              {!partyId && (
                <>
                  <Field label="Name">{(p) => <Input {...p} value={partyName} onChange={(e) => setPartyName(e.target.value)} maxLength={200} />}</Field>
                  {kind === "sale" && <Field label="Phone">{(p) => <Input {...p} type="tel" value={partyPhone} onChange={(e) => setPartyPhone(e.target.value)} maxLength={50} />}</Field>}
                  {partyName && <Checkbox label={`Save this ${kind === "sale" ? "customer" : "supplier"} for next time`} checked={saveParty} onChange={(e) => setSaveParty(e.target.checked)} />}
                </>
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Details" />
            <CardBody className="space-y-4">
              <Field label="Warehouse">
                {(p) => <Select {...p} value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)}>{(warehouses.data ?? []).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}</Select>}
              </Field>
              <Field label={kind === "sale" ? "Invoice number" : "Supplier bill reference"} hint={kind === "sale" ? "Leave empty to number automatically" : undefined}>{(p) => <Input {...p} value={reference} onChange={(e) => setReference(e.target.value)} maxLength={kind === "sale" ? 40 : 80} />}</Field>
            </CardBody>
          </Card>
          <Card>
            <CardHeader title={kind === "sale" ? "Payment received" : "Payment made"} description="Not assumed paid unless you enter an amount." />
            <CardBody className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <Field label="Amount" error={errors["payment.amount"]}>{(p) => <Input {...p} type="number" min={0} step="0.01" value={payAmount} onChange={(e) => setPayAmount(e.target.value)} placeholder="0" />}</Field>
                <Field label="Method">{(p) => <Select {...p} value={payMethod} onChange={(e) => setPayMethod(e.target.value)}>{PAYMENT_METHODS.map((m) => <option key={m} value={m}>{humanize(m)}</option>)}</Select>}</Field>
              </div>
              {lines.length > 0 && <button type="button" className="text-xs font-medium text-brand-700 hover:underline" onClick={() => setPayAmount(est.total.toFixed(2))}>Fill estimated total</button>}
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <Totals rows={[["Subtotal", money(est.subtotal, currency)], ["Discounts", `−${money(est.discount, currency)}`], ["Tax", money(est.tax, currency)], ["Estimated total", money(est.total, currency), true]]} />
              <p className="mt-2 text-xs text-muted">Estimate. Final totals are calculated by the server when you save.</p>
              <Button type="submit" className="mt-4 w-full" loading={saving} icon={<Plus className="size-4" aria-hidden />}>
                {kind === "sale" ? "Save sale" : "Save purchase"}
              </Button>
            </CardBody>
          </Card>
        </div>
      </form>
    </>
  );
}

