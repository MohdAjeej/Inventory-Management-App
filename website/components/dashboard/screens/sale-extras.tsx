"use client";

import { Printer, Undo2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/field";
import { ErrorState, LoadingBlock, Notice } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { ApiError, newRequestId } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import { PAYMENT_METHODS } from "@/lib/api/types";
import { dateTime, humanize, money, qty } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader } from "../parts";

// ------------------------------------------------------------------ return

export function SaleReturnScreen({ id }: { id: number }) {
  const { client, href, can, currency } = useDashboard();
  const router = useRouter();
  const toast = useToast();
  const sale = useApi(() => api.sales.get(client, id), [client, id]);
  const [lines, setLines] = useState<Record<number, { quantity: string; resalable: boolean }>>({});
  const [reason, setReason] = useState("");
  const [refund, setRefund] = useState("");
  const [refundMethod, setRefundMethod] = useState("CASH");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [rid] = useState(newRequestId);
  const back = { href: href(`/sales/${id}`), label: "Sale" };

  if (!can("SALE_CANCEL")) return <><PageHeader title="Record return" back={back} /><Notice tone="warning">You need the “Cancel sales and record returns” permission.</Notice></>;
  if (sale.error) return <Card><ErrorState error={sale.error} onRetry={sale.reload} /></Card>;
  const s = sale.data;
  if (!s) return <Card><LoadingBlock rows={6} /></Card>;
  if (s.status === "CANCELLED") return <><PageHeader title="Record return" back={back} /><Notice tone="warning">Returns can&apos;t be recorded on a cancelled sale.</Notice></>;

  const line = (itemId: number) => lines[itemId] ?? { quantity: "", resalable: true };
  // Value returned is prorated from the line's net + tax (estimate; the server calculates the credit).
  const estimate = s.items.reduce((a, it) => {
    const q = Number(line(it.id).quantity) || 0;
    return a + (it.quantity > 0 ? ((it.netAmount + it.taxAmount) * q) / it.quantity : 0);
  }, 0);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    const items = s.items
      .map((it, i) => ({ it, i, q: Number(line(it.id).quantity) || 0 }))
      .filter((x) => x.q > 0);
    if (!items.length) errs.items = "Enter a quantity for at least one item";
    items.forEach(({ it, q }) => {
      if (q > it.quantity - it.returnedQuantity) errs[`item-${it.id}`] = `At most ${qty(it.quantity - it.returnedQuantity)} can be returned`;
    });
    if (!reason.trim()) errs.reason = "A reason is required";
    if (refund && Number(refund) < 0) errs.refundAmount = "Refund can't be negative";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setSaving(true);
    setFormError(undefined);
    try {
      await api.saleReturn(client, id, {
        items: items.map(({ it, q }) => ({ saleItemId: it.id, quantity: q, resalable: line(it.id).resalable })),
        reason: reason.trim(),
        refundAmount: refund === "" ? null : Number(refund),
        refundMethod,
        notes: notes || undefined,
        clientRequestId: rid,
      });
      toast(`Return recorded${client.mode === "demo" ? " (demo — not saved)" : ""}`);
      router.push(href(`/sales/${id}`));
    } catch (err) {
      if (err instanceof ApiError) { setErrors(err.fieldErrors); setFormError(err.message); }
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader back={back} title={`Return on ${s.invoiceNumber}`} description="Resalable items go back into stock. Damaged items are recorded but not restocked." />
      <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {formError && <Notice tone="danger">{formError}</Notice>}
          <Card>
            <CardHeader title="Items being returned" />
            <CardBody className="space-y-3">
              {errors.items && <p className="text-sm font-medium text-danger">{errors.items}</p>}
              {s.items.map((it) => {
                const left = it.quantity - it.returnedQuantity;
                return (
                  <div key={it.id} className={`rounded-lg border p-3 ${left <= 0 ? "border-line opacity-60" : "border-line"}`}>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <p className="font-medium">{it.productName}{it.variantName && it.variantName !== it.productName ? ` · ${it.variantName}` : ""}</p>
                        <p className="text-xs text-muted">Sold {qty(it.quantity)} {it.unitCode} · returned {qty(it.returnedQuantity)} · {money(it.lineTotal, currency)}</p>
                      </div>
                      {left <= 0 && <span className="text-xs text-muted">Fully returned</span>}
                    </div>
                    {left > 0 && (
                      <div className="mt-3 grid items-end gap-3 sm:grid-cols-2">
                        <Field label={`Quantity to return (max ${qty(left)})`} error={errors[`item-${it.id}`]}>
                          {(p) => <Input {...p} type="number" min={0} max={left} step="any" value={line(it.id).quantity} onChange={(e) => setLines((l) => ({ ...l, [it.id]: { ...line(it.id), quantity: e.target.value } }))} />}
                        </Field>
                        <Checkbox label="Resalable (put back into stock)" checked={line(it.id).resalable} onChange={(e) => setLines((l) => ({ ...l, [it.id]: { ...line(it.id), resalable: e.target.checked } }))} />
                      </div>
                    )}
                  </div>
                );
              })}
            </CardBody>
          </Card>
        </div>
        <div className="space-y-6">
          <Card>
            <CardHeader title="Refund" description={`Received so far: ${money(s.amountPaid, currency)}`} />
            <CardBody className="space-y-4">
              <Field label="Reason" required error={errors.reason}>{(p) => <Input {...p} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} />}</Field>
              <Field label="Refund now" error={errors.refundAmount} hint="Limited to the value returned and the money received. Leave empty to refund nothing now.">{(p) => <Input {...p} type="number" min={0} step="0.01" value={refund} onChange={(e) => setRefund(e.target.value)} />}</Field>
              <Field label="Refund method">{(p) => <Select {...p} value={refundMethod} onChange={(e) => setRefundMethod(e.target.value)}>{PAYMENT_METHODS.map((m) => <option key={m} value={m}>{humanize(m)}</option>)}</Select>}</Field>
              <Field label="Notes">{(p) => <Textarea {...p} value={notes} onChange={(e) => setNotes(e.target.value)} className="min-h-16" maxLength={1000} />}</Field>
              <p className="text-sm text-muted">Estimated value returned: <strong className="text-ink tabular">{money(estimate, currency)}</strong></p>
              <Button type="submit" className="w-full" loading={saving} icon={<Undo2 className="size-4" aria-hidden />}>Record return</Button>
            </CardBody>
          </Card>
        </div>
      </form>
    </>
  );
}

// ------------------------------------------------------------------ receipt

export function SaleReceiptScreen({ id }: { id: number }) {
  const { client, href, me, currency } = useDashboard();
  const sale = useApi(() => api.sales.get(client, id), [client, id]);
  const business = useApi(() => api.business.get(client), [client]);
  if (sale.error) return <Card><ErrorState error={sale.error} onRetry={sale.reload} /></Card>;
  const s = sale.data;
  if (!s) return <Card><LoadingBlock rows={6} /></Card>;
  const b = business.data;
  return (
    <>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 print:hidden">
        <ButtonLink href={href(`/sales/${id}`)} variant="ghost">← Back to sale</ButtonLink>
        <Button icon={<Printer className="size-4" aria-hidden />} onClick={() => window.print()}>Print or save as PDF</Button>
      </div>
      <article id="receipt" className="mx-auto max-w-2xl rounded-xl border border-line bg-white p-8 text-sm text-ink print:max-w-none print:rounded-none print:border-0 print:p-0">
        <header className="flex flex-wrap items-start justify-between gap-4 border-b border-line pb-4">
          <div>
            <h1 className="text-xl font-semibold">{b?.name ?? me.businessName}</h1>
            <p className="text-muted">{[b?.address, b?.city, b?.state, b?.postalCode].filter(Boolean).join(", ")}</p>
            {b?.phone && <p className="text-muted">{b.phone}</p>}
            {b?.taxId && <p className="text-muted">Tax ID: {b.taxId}</p>}
          </div>
          <div className="text-right">
            <p className="text-base font-semibold">{s.status === "CANCELLED" ? "SALES RECEIPT (CANCELLED)" : "SALES RECEIPT"}</p>
            <p>{s.invoiceNumber}</p>
            <p className="text-muted">{dateTime(s.soldAt)}</p>
          </div>
        </header>
        <p className="mt-4"><span className="text-muted">Customer: </span>{s.customerName ?? "Walk-in customer"}{s.customerPhone ? ` · ${s.customerPhone}` : ""}</p>
        <table className="mt-4 w-full">
          <thead>
            <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-muted">
              <th className="py-2 font-semibold">Item</th>
              <th className="py-2 text-right font-semibold">Qty</th>
              <th className="py-2 text-right font-semibold">Price</th>
              <th className="py-2 text-right font-semibold">Disc.</th>
              <th className="py-2 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody>
            {s.items.map((it) => (
              <tr key={it.id} className="border-b border-line align-top">
                <td className="py-2">
                  {it.productName}{it.variantName && it.variantName !== it.productName ? ` · ${it.variantName}` : ""}
                  {it.taxRate > 0 && <div className="text-xs text-muted">Tax {qty(it.taxRate)}%: {money(it.taxAmount, currency)}</div>}
                  {it.returnedQuantity > 0 && <div className="text-xs text-muted">Returned: {qty(it.returnedQuantity)} {it.unitCode}</div>}
                </td>
                <td className="py-2 text-right tabular">{qty(it.quantity)} {it.unitCode}</td>
                <td className="py-2 text-right tabular">{money(it.unitPrice, currency)}</td>
                <td className="py-2 text-right tabular">{it.discount > 0 ? money(it.discount, currency) : "-"}</td>
                <td className="py-2 text-right tabular">{money(it.lineTotal, currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <dl className="ml-auto mt-4 max-w-xs space-y-1">
          {([["Subtotal", s.subtotal], ["Discounts", -s.discountTotal], ["Tax", s.taxTotal], ...(s.returnedTotal ? [["Returned", -s.returnedTotal]] : []), ["Total", s.total], ["Paid", s.amountPaid], ["Balance due", s.outstanding]] as [string, number][]).map(([k, v]) => (
            <div key={k} className={`flex justify-between ${k === "Total" ? "border-t border-line pt-1 font-semibold" : ""}`}><dt className={k === "Total" ? "" : "text-muted"}>{k}</dt><dd className="tabular">{money(v, currency)}</dd></div>
          ))}
        </dl>
        {s.payments.length > 0 && (
          <p className="mt-4 text-xs text-muted">Payments: {s.payments.map((p) => `${p.kind === "REFUND" ? "Refund " : ""}${humanize(p.method)} ${money(p.amount, currency)}`).join(" · ")}</p>
        )}
        {s.notes && <p className="mt-2 text-xs text-muted">Notes: {s.notes}</p>}
        <p className="mt-8 text-center text-muted">Thank you for your business.</p>
      </article>
    </>
  );
}
