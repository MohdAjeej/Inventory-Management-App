"use client";

import { Check, Send, Trash2, Undo2, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { StatusChip } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { ApiError, newRequestId } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { TradeInvoice } from "@/lib/api/types";
import { dateTime, money, qty } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader, ProductPicker } from "../parts";

const demoNote = (mode: string) => (mode === "demo" ? " (demo — not saved)" : "");

// ------------------------------------------------------------------ returns

export function TradeReturnsCard({ invoice, onChange }: { invoice: TradeInvoice; onChange: (i: TradeInvoice) => void }) {
  const { client, can } = useDashboard();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const seller = invoice.role === "SELLER";
  const canRequest = !seller && invoice.status === "POSTED" && can("PURCHASE_MANAGE") && invoice.lines.some((l) => l.quantity - (l.returnedQuantity ?? 0) > 0);
  if (invoice.returns.length === 0 && !canRequest) return null;

  const act = async (fn: () => Promise<TradeInvoice>, msg: string) => {
    try { onChange(await fn()); toast(msg + demoNote(client.mode)); } catch (e) { toast((e as Error).message, "error"); }
  };

  return (
    <Card>
      <CardHeader
        title="Returns"
        description={seller ? "When you accept a return, the goods come back into your stock as a return on the original sale." : "A return request holds the goods in your stock until the seller responds."}
        action={canRequest && <Button size="sm" variant="secondary" icon={<Undo2 className="size-4" aria-hidden />} onClick={() => setOpen(true)}>Request return</Button>}
      />
      {invoice.returns.length === 0 ? <CardBody className="text-sm text-muted">No returns on this invoice.</CardBody> : (
        <ul className="divide-y divide-line">
          {invoice.returns.map((r) => (
            <li key={r.id} className="px-5 py-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-medium">{r.returnNumber} · {money(r.total, invoice.currencyCode)}</p>
                <StatusChip status={r.status} />
              </div>
              <p className="text-muted">{r.reason} · requested {dateTime(r.requestedAt)}</p>
              <p className="text-xs text-ink-2">{r.lines.map((l) => `${qty(l.quantity)} ${l.unitCode} × ${l.productName}`).join(", ")}</p>
              {r.responseNote && <p className="text-xs text-muted">Reply: {r.responseNote}</p>}
              {r.status === "REQUESTED" && (
                <div className="mt-2 flex gap-2">
                  {seller && can("SALE_CANCEL") && (
                    <>
                      <Button size="sm" icon={<Check className="size-4" aria-hidden />} onClick={() => act(() => api.tradeAdmin.acceptReturn(client, r.id), "Return accepted; stock moved back")}>Accept</Button>
                      <Button size="sm" variant="secondary" icon={<X className="size-4" aria-hidden />} onClick={() => act(() => api.tradeAdmin.rejectReturn(client, r.id), "Return rejected")}>Reject</Button>
                    </>
                  )}
                  {!seller && can("PURCHASE_MANAGE") && <Button size="sm" variant="ghost" onClick={() => act(() => api.tradeAdmin.cancelReturn(client, r.id), "Return request cancelled")}>Cancel request</Button>}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <RequestReturnDialog open={open} invoice={invoice} onClose={() => setOpen(false)} onDone={onChange} />
    </Card>
  );
}

function RequestReturnDialog({ open, invoice, onClose, onDone }: { open: boolean; invoice: TradeInvoice; onClose: () => void; onDone: (i: TradeInvoice) => void }) {
  const { client } = useDashboard();
  const toast = useToast();
  const [qtys, setQtys] = useState<Record<number, string>>({});
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [rid, setRid] = useState(newRequestId);
  useEffect(() => { if (open) { setQtys({}); setReason(""); setErrors({}); setRid(newRequestId()); } }, [open]);
  const submit = async () => {
    const errs: Record<string, string> = {};
    const lines = invoice.lines.map((l) => ({ l, q: Number(qtys[l.id]) || 0 })).filter((x) => x.q > 0);
    if (!lines.length) errs.lines = "Enter a quantity for at least one line";
    lines.forEach(({ l, q }) => { if (q > l.quantity - (l.returnedQuantity ?? 0)) errs[`l${l.id}`] = `At most ${qty(l.quantity - (l.returnedQuantity ?? 0))}`; });
    if (!reason.trim()) errs.reason = "A reason is required";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    try {
      onDone(await api.tradeAdmin.requestReturn(client, invoice.id, { lines: lines.map(({ l, q }) => ({ invoiceLineId: l.id, quantity: q })), reason: reason.trim(), clientRequestId: rid }));
      toast(`Return requested${demoNote(client.mode)}`);
      onClose();
    } catch (e) {
      setErrors({ lines: (e as Error).message });
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog open={open} onClose={onClose} title="Request a return" description="The seller is notified. The goods are held in your stock until they respond." footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={submit} loading={saving}>Send request</Button></>}>
      <div className="space-y-3">
        {errors.lines && <Notice tone="danger">{errors.lines}</Notice>}
        {invoice.lines.map((l) => {
          const left = l.quantity - (l.returnedQuantity ?? 0);
          return (
            <div key={l.id} className="grid items-end gap-2 sm:grid-cols-[1fr_9rem]">
              <p className="text-sm"><span className="font-medium">{l.productName}</span><span className="text-muted"> · {qty(left)} {l.unitCode} returnable</span></p>
              <Field label="Quantity" error={errors[`l${l.id}`]}>{(p) => <Input {...p} type="number" min={0} max={left} step="any" disabled={left <= 0} value={qtys[l.id] ?? ""} onChange={(e) => setQtys((x) => ({ ...x, [l.id]: e.target.value }))} />}</Field>
            </div>
          );
        })}
        <Field label="Reason" required error={errors.reason}>{(p) => <Input {...p} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} placeholder="e.g. Edges broken" />}</Field>
      </div>
    </Dialog>
  );
}

// ------------------------------------------------------------------ create / edit draft

interface InvLine { key: string; variantId: number; name: string; unitCode: string; available?: number; quantity: string; price: string; discount: string; taxRate: string }

export function InvoiceFormScreen({ id }: { id?: number }) {
  const editing = id !== undefined;
  const { client, href, can, currency } = useDashboard();
  const router = useRouter();
  const toast = useToast();
  const connections = useApi(() => api.network.connections(client, "ACCEPTED"), [client]);
  const draft = useApi(() => (editing ? api.trade.get(client, id!) : Promise.resolve(null)), [client, id]);
  const [buyer, setBuyer] = useState("");
  const [lines, setLines] = useState<InvLine[]>([]);
  const [notes, setNotes] = useState("");
  const [deliveryNotes, setDeliveryNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState<"draft" | "send" | null>(null);
  const [loaded, setLoaded] = useState(!editing);
  const [rid] = useState(newRequestId);

  useEffect(() => {
    const d = draft.data;
    if (!d || loaded) return;
    setBuyer(String(d.buyer.id));
    setNotes(d.notes ?? "");
    setDeliveryNotes(d.deliveryNotes ?? "");
    setLines(d.lines.filter((l) => l.sellerVariantId).map((l) => ({ key: String(l.id), variantId: l.sellerVariantId!, name: l.productName + (!l.defaultVariant && l.variantName ? ` · ${l.variantName}` : ""), unitCode: l.unitCode, quantity: String(l.quantity), price: String(l.unitPrice), discount: l.discount ? String(l.discount) : "", taxRate: String(l.taxRate) })));
    setLoaded(true);
  }, [draft.data, loaded]);

  const title = editing ? "Edit draft invoice" : "New invoice";
  const back = editing ? { href: href(`/invoices/${id}`), label: "Invoice" } : { href: href("/invoices"), label: "Business invoices" };
  if (!can("SALE_RECORD")) return <><PageHeader title={title} back={back} /><Notice tone="warning">You need the “Record sales” permission to create business invoices.</Notice></>;
  if (draft.error) return <Card><ErrorState error={draft.error} onRetry={draft.reload} /></Card>;
  if (!loaded) return <Card><LoadingBlock rows={6} /></Card>;
  if (editing && draft.data && draft.data.status !== "DRAFT") return <><PageHeader title={title} back={back} /><Notice tone="warning">Only drafts can be edited.</Notice></>;
  const accepted = (connections.data ?? []).filter((c) => c.status === "ACCEPTED");
  const update = (key: string, patch: Partial<InvLine>) => setLines((ls) => ls.map((x) => (x.key === key ? { ...x, ...patch } : x)));

  const submit = async (send: boolean) => {
    const errs: Record<string, string> = {};
    if (!buyer) errs.buyerBusinessId = "Choose the buyer";
    if (!lines.length) errs.lines = "Add at least one product";
    lines.forEach((l, i) => { if (!(Number(l.quantity) > 0)) errs[`lines[${i}].quantity`] = "Quantity must be greater than zero"; });
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(send ? "send" : "draft");
    setFormError(undefined);
    const body = {
      buyerBusinessId: Number(buyer),
      notes: notes || undefined,
      deliveryNotes: deliveryNotes || undefined,
      lines: lines.map((l) => ({ variantId: l.variantId, quantity: Number(l.quantity), unitPrice: l.price === "" ? null : Number(l.price), discount: Number(l.discount) || 0, taxRate: l.taxRate === "" ? null : Number(l.taxRate) })),
      send: editing ? false : send,
      clientRequestId: rid,
    };
    try {
      let inv = editing ? await api.tradeAdmin.update(client, id!, body) : await api.trade.create(client, body);
      if (editing && send) inv = await api.trade.send(client, inv.id);
      toast((send ? "Invoice sent; your stock is reserved" : "Draft saved") + demoNote(client.mode));
      router.push(href(`/invoices/${inv.id}`));
    } catch (err) {
      if (err instanceof ApiError) { setErrors(err.fieldErrors); setFormError(err.message); } else setFormError("Something went wrong");
      setSaving(null);
    }
  };

  const estimate = lines.reduce((a, l) => { const net = Number(l.quantity) * Number(l.price || 0) - (Number(l.discount) || 0); return a + net * (1 + (Number(l.taxRate) || 0) / 100); }, 0);
  return (
    <>
      <PageHeader back={back} title={title} description="Lines are copied from your products. Totals are calculated by the server. Products tracked by batch or serial number can't be invoiced yet." />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {formError && <Notice tone="danger">{formError}</Notice>}
          <Card>
            <CardHeader title="Products" description="Leave price or tax empty to use the product's selling price and tax rate." />
            <CardBody className="space-y-4">
              <ProductPicker
                excludeVariantIds={lines.map((l) => l.variantId)}
                onPick={({ product, variant }) => setLines((ls) => [...ls, { key: `${variant.id}-${Date.now()}`, variantId: variant.id, name: product.name + (!variant.isDefault ? ` · ${variant.name}` : ""), unitCode: product.baseUnit.code, available: variant.stock.available, quantity: "1", price: product.salePrice !== undefined ? String(product.salePrice) : "", discount: "", taxRate: product.taxRate !== undefined ? String(product.taxRate) : "" }])}
              />
              {errors.lines && <p className="text-sm font-medium text-danger">{errors.lines}</p>}
              <ul className="space-y-3">
                {lines.map((l, i) => (
                  <li key={l.key} className="rounded-lg border border-line p-3">
                    <div className="mb-3 flex items-start justify-between gap-3">
                      <p className="font-medium">{l.name}{l.available !== undefined && <span className="text-xs font-normal text-muted"> ({qty(l.available)} {l.unitCode} available)</span>}</p>
                      <button type="button" onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} className="rounded-md p-1.5 text-muted hover:bg-danger-bg hover:text-danger" aria-label={`Remove ${l.name}`}><Trash2 className="size-4" aria-hidden /></button>
                    </div>
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                      <Field label={`Qty (${l.unitCode})`} error={errors[`lines[${i}].quantity`]}>{(p) => <Input {...p} type="number" min={0} step="any" value={l.quantity} onChange={(e) => update(l.key, { quantity: e.target.value })} />}</Field>
                      <Field label="Price">{(p) => <Input {...p} type="number" min={0} step="0.01" value={l.price} onChange={(e) => update(l.key, { price: e.target.value })} />}</Field>
                      <Field label="Discount">{(p) => <Input {...p} type="number" min={0} step="0.01" value={l.discount} placeholder="0" onChange={(e) => update(l.key, { discount: e.target.value })} />}</Field>
                      <Field label="Tax %">{(p) => <Input {...p} type="number" min={0} max={100} value={l.taxRate} onChange={(e) => update(l.key, { taxRate: e.target.value })} />}</Field>
                    </div>
                  </li>
                ))}
              </ul>
            </CardBody>
          </Card>
        </div>
        <div className="space-y-6">
          <Card>
            <CardHeader title="Buyer" />
            <CardBody className="space-y-3">
              {connections.error ? <ErrorState error={connections.error} onRetry={connections.reload} /> : accepted.length === 0 && connections.data ? (
                <Notice tone="warning">You have no connected businesses yet. Connect with a business on the Business network screen first.</Notice>
              ) : (
                <Field label="Connected business" required error={errors.buyerBusinessId}>
                  {(p) => <Select {...p} value={buyer} onChange={(e) => setBuyer(e.target.value)}><option value="">Choose…</option>{accepted.map((c) => <option key={c.business.id} value={c.business.id}>{c.business.name}{c.business.city ? ` · ${c.business.city}` : ""}</option>)}</Select>}
                </Field>
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Notes" />
            <CardBody className="space-y-4">
              <Field label="Notes for the buyer">{(p) => <Textarea {...p} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} className="min-h-16" />}</Field>
              <Field label="Delivery notes">{(p) => <Textarea {...p} value={deliveryNotes} onChange={(e) => setDeliveryNotes(e.target.value)} maxLength={1000} className="min-h-16" />}</Field>
            </CardBody>
          </Card>
          <Card>
            <CardBody className="space-y-2">
              <p className="text-sm text-muted">Estimated total: <span className="font-semibold text-ink tabular">{money(estimate, currency)}</span></p>
              <Button className="w-full" loading={saving === "send"} disabled={!!saving} onClick={() => submit(true)} icon={<Send className="size-4" aria-hidden />}>Send to buyer</Button>
              <Button className="w-full" variant="secondary" loading={saving === "draft"} disabled={!!saving} onClick={() => submit(false)}>{editing ? "Save draft" : "Save as draft"}</Button>
              <p className="text-xs text-muted">Sending reserves your stock for these lines until the buyer approves or rejects.</p>
            </CardBody>
          </Card>
        </div>
      </div>
    </>
  );
}
