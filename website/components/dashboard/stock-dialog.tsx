"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/field";
import { Notice } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { ApiError, newRequestId } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Batch, OperationType, Permission, Warehouse } from "@/lib/api/types";
import { qty } from "@/lib/format";
import { useApi, useDashboard } from "./context";
import { ProductPicker, type PickedVariant } from "./parts";

type Op = OperationType | "TRANSFER";

const OPS: { value: Op; label: string; perm: Permission; help: string }[] = [
  { value: "ADD", label: "Add stock", perm: "STOCK_ADD", help: "Stock received outside a purchase (e.g. returned from a site)." },
  { value: "REDUCE", label: "Reduce stock", perm: "STOCK_REDUCE", help: "Stock used or issued. Recording a sale reduces stock automatically — don't reduce it twice." },
  { value: "ADJUST", label: "Adjust to counted quantity", perm: "STOCK_ADJUST", help: "Enter the corrected on-hand quantity. A reason is required." },
  { value: "DAMAGE", label: "Mark as damaged", perm: "STOCK_ADJUST", help: "Moves available stock into damaged; on hand is unchanged." },
  { value: "RESERVE", label: "Reserve stock", perm: "STOCK_ADJUST", help: "Holds stock for a customer: on hand is unchanged, but it can't be sold elsewhere." },
  { value: "RELEASE", label: "Release reservation", perm: "STOCK_ADJUST", help: "Makes reserved stock available again." },
  { value: "TRANSFER", label: "Transfer between warehouses", perm: "STOCK_TRANSFER", help: "Writes paired transfer-out and transfer-in movements." },
];

export function StockOperationDialog({ open, onClose, onDone, initial }: { open: boolean; onClose: () => void; onDone: () => void; initial?: PickedVariant }) {
  const { client, can } = useDashboard();
  const toast = useToast();
  const settings = useApi(() => api.business.settings(client), [client]);
  // Damaged/reserved stock only exists when the business turns it on (Business settings).
  const damagedReservedOn = settings.data?.trackDamagedReserved ?? false;
  const allowed = OPS.filter((o) => can(o.perm) && (damagedReservedOn || !["DAMAGE", "RESERVE", "RELEASE"].includes(o.value)));
  const [op, setOp] = useState<Op>(allowed[0]?.value ?? "ADD");
  const [picked, setPicked] = useState<PickedVariant | undefined>(initial);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [warehouseId, setWarehouseId] = useState<number>();
  const [toWarehouseId, setToWarehouseId] = useState<number>();
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [requestId, setRequestId] = useState(newRequestId);
  const [lotNumber, setLotNumber] = useState("");
  const [expiryDate, setExpiryDate] = useState("");
  const [serials, setSerials] = useState("");
  const [batchId, setBatchId] = useState("");
  const [fromDamaged, setFromDamaged] = useState(false);
  const [batches, setBatches] = useState<Batch[]>([]);

  useEffect(() => {
    if (!open) return;
    setPicked(initial);
    setQuantity("");
    setReason("");
    setNotes("");
    setLotNumber("");
    setExpiryDate("");
    setSerials("");
    setBatchId("");
    setFromDamaged(false);
    setErrors({});
    setFormError(undefined);
    setRequestId(newRequestId());
    api.warehouses(client).then((w) => {
      setWarehouses(w);
      const def = w.find((x) => x.isDefault) ?? w[0];
      setWarehouseId(def?.id);
      setToWarehouseId(w.find((x) => x.id !== def?.id)?.id);
    }).catch(() => {});
  }, [open, client, initial]);

  useEffect(() => {
    if (!picked?.product.trackBatches) return setBatches([]);
    api.productAdmin.batches(client, picked.variant.id).then(setBatches).catch(() => setBatches([]));
  }, [picked, client]);

  const tracksBatches = !!picked?.product.trackBatches;
  const tracksSerials = !!picked?.product.trackSerials;
  const stockIn = op === "ADD";
  const needsBatch = tracksBatches && (op === "REDUCE" || op === "TRANSFER" || op === "DAMAGE" || op === "RESERVE" || op === "RELEASE");
  const serialList = serials.split(/[\n,]/).map((x) => x.trim()).filter(Boolean);

  const balance = picked?.product.balances.find((b) => b.variantId === picked.variant.id && b.warehouseId === warehouseId);
  const unit = picked?.product.baseUnit;
  const help = OPS.find((o) => o.value === op)?.help;

  const submit = async () => {
    const e: Record<string, string> = {};
    const q = Number(quantity);
    if (!picked) e.product = "Choose a product";
    if (quantity === "" || Number.isNaN(q) || q < 0 || (op !== "ADJUST" && q === 0)) e.quantity = op === "ADJUST" ? "Enter the counted quantity (0 or more)" : "Enter a quantity greater than zero";
    if (unit && quantity && !Number.isNaN(q) && (quantity.split(".")[1]?.length ?? 0) > unit.decimals) e.quantity = `${unit.name} allow at most ${unit.decimals} decimal places`;
    if (op === "ADJUST" && !reason.trim()) e.reason = "A reason is required for adjustments";
    if (op === "TRANSFER" && warehouseId === toWarehouseId) e.toWarehouseId = "Choose a different warehouse";
    if (stockIn && tracksBatches && !lotNumber.trim()) e.lotNumber = "Enter the lot number for this stock";
    if (needsBatch && !batchId) e.batchId = "Choose the batch";
    if (tracksSerials && op !== "ADJUST" && q > 0 && serialList.length !== q) e.serials = `Enter exactly ${q} serial number${q === 1 ? "" : "s"}`;
    setErrors(e);
    if (Object.keys(e).length || !picked) return;
    setSaving(true);
    setFormError(undefined);
    try {
      if (op === "TRANSFER") {
        await api.stock.transfer(client, { variantId: picked.variant.id, fromWarehouseId: warehouseId!, toWarehouseId: toWarehouseId!, quantity: q, batchId: batchId ? Number(batchId) : null, serials: tracksSerials ? serialList : null, reason: reason || undefined, notes: notes || undefined, clientRequestId: requestId });
      } else {
        await api.stock.operate(client, {
          type: op, variantId: picked.variant.id, warehouseId, quantity: q, reason: reason || undefined, notes: notes || undefined,
          batchId: needsBatch && batchId ? Number(batchId) : null,
          lotNumber: stockIn && tracksBatches ? lotNumber.trim() : undefined,
          expiryDate: stockIn && tracksBatches && expiryDate ? expiryDate : undefined,
          serials: tracksSerials && op !== "ADJUST" ? serialList : null,
          fromDamaged: op === "REDUCE" ? fromDamaged : false,
          clientRequestId: requestId,
        });
      }
      toast(client.mode === "demo" ? "Stock updated (demo — not saved)" : "Stock updated");
      onDone();
      onClose();
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        setFormError(err.message);
      } else setFormError("Something went wrong");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Update stock"
      description="Every change is recorded permanently in the stock history."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving} disabled={!allowed.length}>
            Save
          </Button>
        </>
      }
    >
      {!allowed.length ? (
        <Notice tone="warning">You don&apos;t have permission to change stock.</Notice>
      ) : (
        <div className="space-y-4">
          {formError && <Notice tone="danger">{formError}</Notice>}
          <Field label="Operation">
            {(p) => (
              <Select {...p} value={op} onChange={(e) => setOp(e.target.value as Op)}>
                {allowed.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          {help && <p className="-mt-2 text-xs text-muted">{help}</p>}
          {picked ? (
            <div className="flex items-center justify-between rounded-lg border border-line bg-canvas px-3 py-2 text-sm">
              <span className="min-w-0 truncate font-medium">
                {picked.product.name}
                {!picked.variant.isDefault && ` · ${picked.variant.name}`}
              </span>
              {!initial && (
                <button type="button" className="text-xs font-medium text-brand-700 hover:underline" onClick={() => setPicked(undefined)}>
                  Change
                </button>
              )}
            </div>
          ) : (
            <div>
              <ProductPicker label="Product" onPick={setPicked} />
              {errors.product && <p className="mt-1 text-xs font-medium text-danger">{errors.product}</p>}
            </div>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={op === "TRANSFER" ? "From warehouse" : "Warehouse"}>
              {(p) => (
                <Select {...p} value={warehouseId ?? ""} onChange={(e) => setWarehouseId(Number(e.target.value))}>
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            {op === "TRANSFER" ? (
              <Field label="To warehouse" error={errors.toWarehouseId}>
                {(p) => (
                  <Select {...p} value={toWarehouseId ?? ""} onChange={(e) => setToWarehouseId(Number(e.target.value))}>
                    {warehouses.map((w) => (
                      <option key={w.id} value={w.id}>
                        {w.name}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
            ) : (
              <Field label={op === "ADJUST" ? "Counted on-hand quantity" : "Quantity"} error={errors.quantity} required hint={balance ? `Now: ${qty(balance.onHand)} on hand, ${qty(balance.available)} available ${unit?.code ?? ""}` : undefined}>
                {(p) => <Input {...p} type="number" inputMode="decimal" min={0} step="any" value={quantity} onChange={(e) => setQuantity(e.target.value)} />}
              </Field>
            )}
          </div>
          {op === "TRANSFER" && (
            <Field label="Quantity" error={errors.quantity} required hint={balance ? `${qty(balance.available)} ${unit?.code ?? ""} available in the source warehouse` : undefined}>
              {(p) => <Input {...p} type="number" inputMode="decimal" min={0} step="any" value={quantity} onChange={(e) => setQuantity(e.target.value)} />}
            </Field>
          )}
          {stockIn && tracksBatches && (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Lot number" required error={errors.lotNumber}>{(p) => <Input {...p} value={lotNumber} onChange={(e) => setLotNumber(e.target.value)} maxLength={80} />}</Field>
              <Field label="Expiry date" error={errors.expiryDate}>{(p) => <Input {...p} type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)} />}</Field>
            </div>
          )}
          {needsBatch && (
            <Field label="Batch" required error={errors.batchId}>
              {(p) => (
                <Select {...p} value={batchId} onChange={(e) => setBatchId(e.target.value)}>
                  <option value="">Choose…</option>
                  {batches.map((b) => <option key={b.id} value={b.id}>{b.lotNumber ?? `Batch ${b.id}`}{b.expiryDate ? ` · expires ${b.expiryDate}` : ""} · {qty(b.onHand)} on hand</option>)}
                </Select>
              )}
            </Field>
          )}
          {tracksSerials && op !== "ADJUST" && (
            <Field label="Serial numbers" required error={errors.serials} hint="One per line or comma-separated; one per unit">{(p) => <Textarea {...p} value={serials} onChange={(e) => setSerials(e.target.value)} className="min-h-16 font-mono" />}</Field>
          )}
          {op === "REDUCE" && damagedReservedOn && <Checkbox label="Take from damaged stock" description="Write off damaged goods instead of available stock" checked={fromDamaged} onChange={(e) => setFromDamaged(e.target.checked)} />}
          <Field label="Reason" error={errors.reason} required={op === "ADJUST"}>
            {(p) => <Input {...p} value={reason} onChange={(e) => setReason(e.target.value)} placeholder={op === "ADJUST" ? "e.g. Counting error" : "Optional"} maxLength={200} />}
          </Field>
          <Field label="Notes">{(p) => <Textarea {...p} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} className="min-h-16" />}</Field>
        </div>
      )}
    </Dialog>
  );
}
