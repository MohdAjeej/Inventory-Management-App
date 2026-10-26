/**
 * DEMO DATA PROVIDER — handlers for the screens added for parity with the Android app
 * (catalog setup, variants, warehouses, stock counts, returns, business network, trade returns,
 * product links, permission overrides). Same paths, shapes and rules as the backend; in memory only.
 */
import { ApiError } from "@/lib/api/client";
import type * as T from "@/lib/api/types";
import { ROLE_DEFAULTS } from "@/lib/permissions";
import { applyMovement, balKey, findVariant, round2, unitOf, type DemoState } from "./store";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Handler = (ctx: { m: RegExpMatchArray; q: Record<string, unknown>; body: any; me: T.Me; s: DemoState; actor: { id: number; name: string } }) => unknown;

interface Extra {
  conversions: T.UnitConversion[];
  locations: T.Location[];
  counts: T.StockCount[];
  mappings: T.ProductMapping[];
  discoverable: boolean;
  overrides: Record<number, Partial<Record<T.Permission, boolean>>>;
  seq: number;
}

const extras = new WeakMap<DemoState, Extra>();
export function extra(s: DemoState): Extra {
  let e = extras.get(s);
  if (!e) {
    e = {
      conversions: [{ id: 1, fromUnit: s.units.find((u) => u.code === "box")!, toUnit: s.units.find((u) => u.code === "pcs")!, factor: 12 }],
      locations: [
        { id: 1, warehouseId: 1, code: "A-01", description: "Cables rack", archived: false },
        { id: 2, warehouseId: 1, code: "B-04", description: "Lighting shelf", archived: false },
      ],
      counts: [],
      mappings: [],
      discoverable: true,
      overrides: {},
      seq: 1000,
    };
    extras.set(s, e);
  }
  return e;
}

/** Effective permissions for a demo member, including per-person overrides set on the Employee screen. */
export function effectivePermissions(s: DemoState, membershipId: number, role: T.Role): T.Permission[] {
  const set = new Set(ROLE_DEFAULTS[role]);
  for (const [p, on] of Object.entries(extra(s).overrides[membershipId] ?? {})) {
    if (on) set.add(p as T.Permission);
    else set.delete(p as T.Permission);
  }
  if (role === "ADMIN") set.add("EMPLOYEE_MANAGE");
  return [...set];
}

function forbid(me: T.Me, ...perms: T.Permission[]) {
  if (!perms.some((p) => me.permissions.includes(p))) throw new ApiError({ status: 403, code: "FORBIDDEN", message: "You don't have permission to do this." });
}
function notFound(what = "Record"): never {
  throw new ApiError({ status: 404, code: "NOT_FOUND", message: `${what} not found` });
}
function invalid(field: string, message: string): never {
  throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message, fieldErrors: { [field]: message } });
}
const now = () => new Date().toISOString();

function countSummary(c: T.StockCount): T.StockCountSummary {
  return { ...c.summary, lineCount: c.lines.length, countedLines: c.lines.filter((l) => l.countedQty !== undefined && l.countedQty !== null).length };
}

export const parityRoutes: [string, RegExp, Handler][] = [
  // ---- units & conversions
  ["POST", /^\/units$/, ({ s, me, body }) => {
    forbid(me, "CATEGORY_MANAGE");
    if (!body.code?.trim()) invalid("code", "Code is required");
    if (s.units.some((u) => u.code.toLowerCase() === body.code.trim().toLowerCase())) invalid("code", "A unit with this code already exists");
    const u: T.Unit = { id: extra(s).seq++, code: body.code.trim(), name: body.name.trim(), decimals: Number(body.decimals) || 0, archived: false };
    s.units.push(u);
    return u;
  }],
  ["PUT", /^\/units\/(\d+)$/, ({ s, m, me, body }) => {
    forbid(me, "CATEGORY_MANAGE");
    const u = s.units.find((x) => x.id === Number(m[1])) ?? notFound("Unit");
    Object.assign(u, { code: body.code, name: body.name, decimals: Number(body.decimals) });
    return u;
  }],
  ["POST", /^\/units\/(\d+)\/(archive|restore)$/, ({ s, m, me }) => {
    forbid(me, "CATEGORY_MANAGE");
    const u = s.units.find((x) => x.id === Number(m[1])) ?? notFound("Unit");
    if (m[2] === "archive" && s.products.some((p) => !p.archived && p.unitId === u.id)) throw new ApiError({ status: 409, code: "CONFLICT", message: "This unit is used by active products" });
    u.archived = m[2] === "archive";
    return u;
  }],
  ["GET", /^\/unit-conversions$/, ({ s }) => extra(s).conversions],
  ["POST", /^\/unit-conversions$/, ({ s, me, body }) => {
    forbid(me, "CATEGORY_MANAGE");
    const from = s.units.find((u) => u.id === Number(body.fromUnitId)) ?? notFound("Unit");
    const to = s.units.find((u) => u.id === Number(body.toUnitId)) ?? notFound("Unit");
    if (!(Number(body.factor) > 0)) invalid("factor", "Factor must be positive");
    const c: T.UnitConversion = { id: extra(s).seq++, fromUnit: from, toUnit: to, factor: Number(body.factor) };
    extra(s).conversions.push(c);
    return c;
  }],
  ["DELETE", /^\/unit-conversions\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "CATEGORY_MANAGE");
    extra(s).conversions = extra(s).conversions.filter((c) => c.id !== Number(m[1]));
    return undefined;
  }],
  ["GET", /^\/unit-conversions\/convert$/, ({ s, q }) => {
    const from = Number(q.fromUnitId);
    const to = Number(q.toUnitId);
    const quantity = Number(q.quantity);
    const unit = (id: number) => s.units.find((u) => u.id === id) ?? notFound("Unit");
    if (from === to) return { quantity, fromUnit: unit(from).code, result: quantity, toUnit: unit(to).code };
    const direct = extra(s).conversions.find((c) => c.fromUnit.id === from && c.toUnit.id === to);
    const inverse = extra(s).conversions.find((c) => c.fromUnit.id === to && c.toUnit.id === from);
    if (!direct && !inverse) throw new ApiError({ status: 400, code: "BAD_REQUEST", message: "No conversion between these units" });
    const result = direct ? quantity * direct.factor : quantity / inverse!.factor;
    return { quantity, fromUnit: unit(from).code, result: Math.round(result * 1e6) / 1e6, toUnit: unit(to).code };
  }],

  // ---- categories & attributes
  ["POST", /^\/categories$/, ({ s, me, body }) => {
    forbid(me, "CATEGORY_MANAGE");
    if (!body.name?.trim()) invalid("name", "Name is required");
    const c = { id: extra(s).seq++, name: body.name.trim(), description: body.description, parentId: body.parentId ?? undefined, archived: false };
    s.categories.push(c);
    return c;
  }],
  ["PUT", /^\/categories\/(\d+)$/, ({ s, m, me, body }) => {
    forbid(me, "CATEGORY_MANAGE");
    const c = s.categories.find((x) => x.id === Number(m[1])) ?? notFound("Category");
    if (!body.name?.trim()) invalid("name", "Name is required");
    Object.assign(c, { name: body.name.trim(), description: body.description, parentId: body.parentId ?? undefined });
    return c;
  }],
  ["POST", /^\/categories\/(\d+)\/(archive|restore)$/, ({ s, m, me }) => {
    forbid(me, "CATEGORY_MANAGE");
    const c = s.categories.find((x) => x.id === Number(m[1])) ?? notFound("Category");
    c.archived = m[2] === "archive";
    return c;
  }],
  ["POST", /^\/attributes$/, ({ s, me, body }) => {
    forbid(me, "CATEGORY_MANAGE");
    if (s.attributes.some((a) => a.key === body.key && (a.categoryId ?? null) === (body.categoryId ?? null))) invalid("key", "This key is already used");
    const a: T.Attribute = { id: extra(s).seq++, ...body, categoryId: body.categoryId ?? undefined, categoryName: s.categories.find((c) => c.id === body.categoryId)?.name, archived: false };
    s.attributes.push(a);
    return a;
  }],
  ["PUT", /^\/attributes\/(\d+)$/, ({ s, m, me, body }) => {
    forbid(me, "CATEGORY_MANAGE");
    const a = s.attributes.find((x) => x.id === Number(m[1])) ?? notFound("Field");
    Object.assign(a, body, { categoryId: body.categoryId ?? undefined, categoryName: s.categories.find((c) => c.id === body.categoryId)?.name });
    return a;
  }],
  ["POST", /^\/attributes\/(\d+)\/(archive|restore)$/, ({ s, m, me }) => {
    forbid(me, "CATEGORY_MANAGE");
    const a = s.attributes.find((x) => x.id === Number(m[1])) ?? notFound("Field");
    a.archived = m[2] === "archive";
    return a;
  }],
  ["GET", /^\/form-schema$/, ({ s, q }) => {
    const ids = new Set<number | undefined>([undefined]);
    for (let c = s.categories.find((x) => x.id === Number(q.categoryId)); c; c = s.categories.find((x) => x.id === c!.parentId)) ids.add(c.id);
    return { categoryId: q.categoryId ? Number(q.categoryId) : undefined, fields: s.attributes.filter((a) => !a.archived && ids.has(a.categoryId)), trackBatches: s.settings.trackBatches, trackExpiry: s.settings.trackExpiry, trackSerials: s.settings.trackSerials, trackDamagedReserved: s.settings.trackDamagedReserved } satisfies T.FormSchema;
  }],

  // ---- warehouses & locations
  ["POST", /^\/warehouses$/, ({ s, me, body }) => {
    forbid(me, "WAREHOUSE_MANAGE");
    if (!body.name?.trim()) invalid("name", "Name is required");
    if (s.warehouses.some((w) => w.code.toUpperCase() === String(body.code).toUpperCase())) invalid("code", "This code is already used");
    const w = { id: extra(s).seq++, name: body.name.trim(), code: String(body.code).toUpperCase(), address: body.address, branch: !!body.branch, isDefault: false, archived: false };
    s.warehouses.push(w);
    return { ...w, productCount: 0, totalQuantity: 0 };
  }],
  ["PUT", /^\/warehouses\/(\d+)$/, ({ s, m, me, body }) => {
    forbid(me, "WAREHOUSE_MANAGE");
    const w = s.warehouses.find((x) => x.id === Number(m[1])) ?? notFound("Warehouse");
    Object.assign(w, { name: body.name, code: String(body.code).toUpperCase(), address: body.address, branch: !!body.branch });
    return { ...w, productCount: 0, totalQuantity: 0 };
  }],
  ["POST", /^\/warehouses\/(\d+)\/(archive|restore|default)$/, ({ s, m, me }) => {
    forbid(me, "WAREHOUSE_MANAGE");
    const w = s.warehouses.find((x) => x.id === Number(m[1])) ?? notFound("Warehouse");
    if (m[2] === "default") s.warehouses.forEach((x) => (x.isDefault = x.id === w.id));
    else {
      if (m[2] === "archive" && w.isDefault) throw new ApiError({ status: 409, code: "CONFLICT", message: "The default warehouse can't be archived" });
      if (m[2] === "archive" && s.products.some((p) => p.variants.some((v) => (s.balances.get(balKey(v.id, w.id))?.onHand ?? 0) !== 0))) throw new ApiError({ status: 409, code: "CONFLICT", message: "A warehouse can only be archived when it is empty" });
      w.archived = m[2] === "archive";
    }
    return { ...w, productCount: 0, totalQuantity: 0 };
  }],
  ["GET", /^\/warehouses\/(\d+)\/locations$/, ({ s, m }) => extra(s).locations.filter((l) => l.warehouseId === Number(m[1]))],
  ["POST", /^\/warehouses\/(\d+)\/locations$/, ({ s, m, me, body }) => {
    forbid(me, "WAREHOUSE_MANAGE");
    const l: T.Location = { id: extra(s).seq++, warehouseId: Number(m[1]), code: body.code, description: body.description, archived: false };
    extra(s).locations.push(l);
    return l;
  }],
  ["PUT", /^\/locations\/(\d+)$/, ({ s, m, me, body }) => {
    forbid(me, "WAREHOUSE_MANAGE");
    const l = extra(s).locations.find((x) => x.id === Number(m[1])) ?? notFound("Location");
    Object.assign(l, { code: body.code, description: body.description });
    return l;
  }],
  ["POST", /^\/locations\/(\d+)\/(archive|restore)$/, ({ s, m, me }) => {
    forbid(me, "WAREHOUSE_MANAGE");
    const l = extra(s).locations.find((x) => x.id === Number(m[1])) ?? notFound("Location");
    l.archived = m[2] === "archive";
    return l;
  }],

  // ---- variants, batches, serials
  ["GET", /^\/variants\/(\d+)\/(batches|serials)$/, () => []],
  ["DELETE", /^\/products\/(\d+)\/images\/(\d+)$/, () => {
    throw new ApiError({ status: 400, code: "DEMO_ONLY", message: "Images aren't available in demo mode." });
  }],

  // ---- stock counts
  ["GET", /^\/stock-counts$/, ({ s, me, q }) => {
    forbid(me, "STOCK_COUNT");
    const rows = [...extra(s).counts].reverse().map(countSummary);
    const size = Number(q.size ?? 20);
    const page = Number(q.page ?? 0);
    return { content: rows.slice(page * size, page * size + size), page, size, totalElements: rows.length, totalPages: Math.max(1, Math.ceil(rows.length / size)) };
  }],
  ["GET", /^\/stock-counts\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "STOCK_COUNT");
    const c = extra(s).counts.find((x) => x.summary.id === Number(m[1])) ?? notFound("Stock count");
    return { summary: countSummary(c), lines: c.lines };
  }],
  ["POST", /^\/stock-counts$/, ({ s, me, body, actor }) => {
    forbid(me, "STOCK_COUNT");
    const w = s.warehouses.find((x) => x.id === Number(body.warehouseId)) ?? notFound("Warehouse");
    const catIds = new Set<number>();
    if (body.categoryId) {
      catIds.add(Number(body.categoryId));
      s.categories.filter((c) => c.parentId === Number(body.categoryId)).forEach((c) => catIds.add(c.id));
    }
    const id = extra(s).seq++;
    const lines: T.CountLine[] = s.products
      .filter((p) => !p.archived && (!body.categoryId || (p.categoryId !== undefined && catIds.has(p.categoryId))))
      .flatMap((p) => p.variants.filter((v) => !v.archived).map((v) => ({ id: extra(s).seq++, variantId: v.id, productId: p.id, productName: p.name, variantName: v.name, sku: v.sku, unitCode: unitOf(s, p).code, expectedQty: s.balances.get(balKey(v.id, w.id))?.onHand ?? 0 })));
    const c: T.StockCount = { summary: { id, warehouseId: w.id, warehouseName: w.name, status: "OPEN", notes: body.notes, createdBy: actor.name, createdAt: now(), lineCount: lines.length, countedLines: 0 }, lines };
    extra(s).counts.push(c);
    return { summary: countSummary(c), lines };
  }],
  ["PUT", /^\/stock-counts\/(\d+)\/lines$/, ({ s, m, me, body }) => {
    forbid(me, "STOCK_COUNT");
    const c = extra(s).counts.find((x) => x.summary.id === Number(m[1])) ?? notFound("Stock count");
    if (c.summary.status !== "OPEN") throw new ApiError({ status: 409, code: "CONFLICT", message: "This count is closed" });
    for (const u of body.lines ?? []) {
      const l = c.lines.find((x) => x.id === u.lineId);
      if (!l) continue;
      if (u.countedQty === null || u.countedQty === undefined) { delete l.countedQty; delete l.difference; continue; }
      if (u.countedQty < 0) invalid("countedQty", "Counted quantity cannot be negative");
      l.countedQty = Number(u.countedQty);
      l.difference = round2(l.countedQty - l.expectedQty);
    }
    return { summary: countSummary(c), lines: c.lines };
  }],
  ["POST", /^\/stock-counts\/(\d+)\/reconcile$/, ({ s, m, me, actor }) => {
    forbid(me, "STOCK_COUNT");
    forbid(me, "STOCK_ADJUST");
    const c = extra(s).counts.find((x) => x.summary.id === Number(m[1])) ?? notFound("Stock count");
    if (c.summary.status !== "OPEN") throw new ApiError({ status: 409, code: "CONFLICT", message: "This count is closed" });
    let adjusted = 0;
    for (const l of c.lines) {
      if (l.countedQty === undefined || l.countedQty === null) continue;
      const delta = l.countedQty - (s.balances.get(balKey(l.variantId, c.summary.warehouseId))?.onHand ?? 0);
      if (delta === 0) continue;
      applyMovement(s, { type: "COUNT_ADJUST", variantId: l.variantId, warehouseId: c.summary.warehouseId, quantity: delta, reason: `Stock count #${c.summary.id}`, actor, at: now() });
      adjusted++;
    }
    c.summary.status = "RECONCILED";
    c.summary.reconciledAt = now();
    return { summary: countSummary(c), adjustedLines: adjusted };
  }],
  ["POST", /^\/stock-counts\/(\d+)\/cancel$/, ({ s, m, me }) => {
    forbid(me, "STOCK_COUNT");
    const c = extra(s).counts.find((x) => x.summary.id === Number(m[1])) ?? notFound("Stock count");
    if (c.summary.status !== "OPEN") throw new ApiError({ status: 409, code: "CONFLICT", message: "This count is closed" });
    c.summary.status = "CANCELLED";
    return countSummary(c);
  }],

  // ---- sale returns
  ["POST", /^\/sales\/(\d+)\/returns$/, ({ s, m, me, body, actor }) => {
    forbid(me, "SALE_CANCEL");
    const sale = s.sales.find((x) => x.id === Number(m[1])) ?? notFound("Sale");
    if (sale.status === "CANCELLED") throw new ApiError({ status: 409, code: "CONFLICT", message: "Returns can't be recorded on a cancelled sale" });
    if (!body.reason?.trim()) invalid("reason", "A reason is required");
    let credit = 0;
    const items: T.SaleReturn["items"] = [];
    for (const r of body.items ?? []) {
      const it = sale.items.find((x) => x.id === r.saleItemId) ?? notFound("Sale item");
      const left = it.quantity - it.returnedQuantity;
      if (r.quantity > left) invalid("items", `You can return at most ${left} of ${it.productName}`);
      const share = r.quantity / it.quantity;
      const net = round2(it.netAmount * share);
      const tax = round2(it.taxAmount * share);
      credit += net + tax;
      it.returnedQuantity += r.quantity;
      items.push({ saleItemId: it.id, productName: it.productName, quantity: r.quantity, resalable: !!r.resalable, netAmount: net, taxAmount: tax });
      // Resalable items are restocked; damaged ones are recorded but not restocked.
      if (r.resalable) applyMovement(s, { type: "RETURN_IN", variantId: it.variantId, warehouseId: s.warehouses.find((w) => w.name === it.warehouseName)?.id ?? 1, quantity: r.quantity, reason: `Return on ${sale.invoiceNumber}`, actor, at: now() });
    }
    credit = round2(credit);
    const refund = Math.min(Number(body.refundAmount ?? 0) || 0, credit, sale.amountPaid);
    sale.returns.push({ id: extra(s).seq++, returnNumber: `RET-${String(sale.returns.length + 1).padStart(5, "0")}`, returnedAt: now(), reason: body.reason, creditAmount: credit, refundAmount: refund, refundMethod: body.refundMethod, recordedBy: actor.name, items });
    if (refund > 0) sale.payments.push({ id: extra(s).seq++, kind: "REFUND", amount: refund, method: body.refundMethod ?? "CASH", paidAt: now(), recordedBy: actor.name });
    sale.returnedTotal = round2(sale.returnedTotal + credit);
    sale.amountPaid = round2(sale.amountPaid - refund);
    sale.outstanding = round2(Math.max(sale.total - sale.returnedTotal - sale.amountPaid, 0));
    sale.paymentStatus = sale.total - sale.returnedTotal <= 0 || sale.amountPaid >= sale.total - sale.returnedTotal ? "PAID" : sale.amountPaid <= 0 ? "UNPAID" : "PARTIALLY_PAID";
    return sale;
  }],

  // ---- business network
  ["GET", /^\/network\/search$/, ({ s, me, q }) => {
    forbid(me, "SALE_RECORD", "PURCHASE_MANAGE", "BUSINESS_SETTINGS");
    const term = String(q.q ?? "").trim().toLowerCase();
    if (term.length < 2) invalid("q", "Type at least 2 characters");
    const directory: T.BusinessCard[] = [
      ...s.connections.map((c) => c.business),
      { id: 904, name: "Hilltop Hardware (sample)", businessType: "Retailer", city: "Kolhapur", businessCode: "HILL42GH" },
      { id: 905, name: "Coastal Paints Depot (sample)", businessType: "Distributor", city: "Goa", businessCode: "COAS77IJ" },
    ];
    return directory
      .filter((b) => b.name.toLowerCase().includes(term) || b.businessCode?.toLowerCase() === term)
      .slice(0, 20)
      .map((b) => {
        const c = s.connections.find((x) => x.business.id === b.id && x.status !== "REMOVED" && x.status !== "REJECTED");
        return { business: b, connectionId: c?.id, connectionStatus: c?.status, direction: c?.direction } satisfies T.SearchResult;
      });
  }],
  ["PUT", /^\/network\/me\/discoverable$/, ({ s, me, body }) => {
    forbid(me, "BUSINESS_SETTINGS");
    extra(s).discoverable = !!body.discoverable;
    return { businessCode: s.business.businessCode, name: s.business.name, discoverable: extra(s).discoverable };
  }],
  ["POST", /^\/network\/connections$/, ({ s, me, body }) => {
    forbid(me, "BUSINESS_SETTINGS");
    const existing = s.connections.find((c) => c.business.id === Number(body.businessId) && (c.status === "PENDING" || c.status === "ACCEPTED"));
    if (existing?.status === "PENDING" && existing.direction === "INCOMING") {
      existing.status = "ACCEPTED";
      existing.respondedAt = now();
      return existing;
    }
    if (existing) throw new ApiError({ status: 409, code: "CONFLICT", message: "You're already connected or have a pending request" });
    const card = [{ id: 904, name: "Hilltop Hardware (sample)", businessType: "Retailer", city: "Kolhapur", businessCode: "HILL42GH" }, { id: 905, name: "Coastal Paints Depot (sample)", businessType: "Distributor", city: "Goa", businessCode: "COAS77IJ" }].find((b) => b.id === Number(body.businessId)) ?? notFound("Business");
    const c: T.Connection = { id: extra(s).seq++, status: "PENDING", direction: "OUTGOING", business: card, message: body.message, createdAt: now() };
    s.connections.push(c);
    return c;
  }],
  ["POST", /^\/network\/connections\/(\d+)\/(accept|reject|remove)$/, ({ s, m, me }) => {
    forbid(me, "BUSINESS_SETTINGS");
    const c = s.connections.find((x) => x.id === Number(m[1])) ?? notFound("Connection");
    if ((m[2] === "accept" || m[2] === "reject") && c.direction !== "INCOMING") throw new ApiError({ status: 409, code: "CONFLICT", message: "Only the business that received the request can answer it" });
    c.status = m[2] === "accept" ? "ACCEPTED" : m[2] === "reject" ? "REJECTED" : "REMOVED";
    c.respondedAt = now();
    return c;
  }],

  // ---- trade: draft editing, returns, product links
  ["PUT", /^\/trade\/invoices\/(\d+)$/, () => {
    throw new ApiError({ status: 400, code: "DEMO_ONLY", message: "Editing drafts isn't simulated in demo mode. Delete the draft and create a new one." });
  }],
  ["POST", /^\/trade\/invoices\/(\d+)\/returns$/, ({ s, m, me, body, actor }) => {
    forbid(me, "PURCHASE_MANAGE");
    const inv = s.invoices.find((i) => i.id === Number(m[1])) ?? notFound("Invoice");
    if (inv.role !== "BUYER" || inv.status !== "POSTED") throw new ApiError({ status: 409, code: "CONFLICT", message: "Returns can only be requested on approved invoices you received" });
    if (!body.reason?.trim()) invalid("reason", "A reason is required");
    let total = 0;
    const lines = (body.lines ?? []).map((r: { invoiceLineId: number; quantity: number }) => {
      const l = inv.lines.find((x) => x.id === r.invoiceLineId) ?? notFound("Invoice line");
      if (r.quantity > l.quantity - (l.returnedQuantity ?? 0)) invalid("lines", `At most ${l.quantity - (l.returnedQuantity ?? 0)} of ${l.productName} can be returned`);
      const amount = round2((l.lineTotal * r.quantity) / l.quantity);
      total += amount;
      return { invoiceLineId: l.id, productName: l.productName, variantName: l.variantName, unitCode: l.unitCode, quantity: r.quantity, amount };
    });
    inv.returns.push({ id: extra(s).seq++, returnNumber: `${inv.invoiceNumber}-R${inv.returns.length + 1}`, status: "REQUESTED", reason: body.reason.trim(), total: round2(total), requestedAt: now(), lines });
    inv.events.push({ action: "RETURN_REQUESTED", businessId: s.business.id, businessName: s.business.name, actorName: actor.name, note: body.reason, at: now() });
    return inv;
  }],
  ["POST", /^\/trade\/returns\/(\d+)\/(accept|reject|cancel)$/, ({ s, m, me, actor }) => {
    const inv = s.invoices.find((i) => i.returns.some((r) => r.id === Number(m[1]))) ?? notFound("Return");
    const r = inv.returns.find((x) => x.id === Number(m[1]))!;
    if (r.status !== "REQUESTED") throw new ApiError({ status: 409, code: "CONFLICT", message: "This return has already been answered" });
    if (m[2] === "cancel") {
      forbid(me, "PURCHASE_MANAGE");
      r.status = "CANCELLED";
    } else {
      forbid(me, "SALE_CANCEL");
      if (inv.role !== "SELLER") throw new ApiError({ status: 409, code: "CONFLICT", message: "Only the seller can answer a return" });
      r.status = m[2] === "accept" ? "ACCEPTED" : "REJECTED";
      if (r.status === "ACCEPTED") for (const l of r.lines) {
        const line = inv.lines.find((x) => x.id === l.invoiceLineId);
        if (line) line.returnedQuantity = (line.returnedQuantity ?? 0) + l.quantity;
      }
    }
    r.respondedAt = now();
    inv.events.push({ action: `RETURN_${r.status}`, businessId: s.business.id, businessName: s.business.name, actorName: actor.name, note: r.returnNumber, at: now() });
    return inv;
  }],
  ["GET", /^\/trade\/mappings$/, ({ s, me }) => {
    forbid(me, "PURCHASE_MANAGE");
    return extra(s).mappings;
  }],
  ["DELETE", /^\/trade\/mappings\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "PURCHASE_MANAGE");
    extra(s).mappings = extra(s).mappings.filter((x) => x.id !== Number(m[1]));
    return undefined;
  }],

  // ---- employees
  ["GET", /^\/employees\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    const e = s.employees.find((x) => x.membershipId === Number(m[1])) ?? notFound("Employee");
    return { ...e, overrides: extra(s).overrides[e.membershipId] ?? {}, permissions: effectivePermissions(s, e.membershipId, e.role) };
  }],
  ["PUT", /^\/employees\/(\d+)\/permissions$/, ({ s, m, me, body }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    const e = s.employees.find((x) => x.membershipId === Number(m[1])) ?? notFound("Employee");
    if (e.membershipId === me.membershipId) throw new ApiError({ status: 400, code: "BAD_REQUEST", message: "You can't change your own permissions" });
    extra(s).overrides[e.membershipId] = body.overrides ?? {};
    return { ...e, overrides: extra(s).overrides[e.membershipId], permissions: effectivePermissions(s, e.membershipId, e.role) };
  }],
];

/** Records a product link when a supplier invoice is approved into one of our products. */
export function rememberMapping(s: DemoState, seller: T.BusinessCard, sellerProductName: string, buyerVariantId: number) {
  const { product, variant } = findVariant(s, buyerVariantId);
  const list = extra(s).mappings;
  const existing = list.find((m) => m.seller.id === seller.id && m.sellerProductName === sellerProductName);
  const row: T.ProductMapping = { id: existing?.id ?? extra(s).seq++, seller, sellerVariantId: 0, sellerProductName, buyerVariantId, buyerProductId: product.id, buyerProductName: product.name, buyerVariantName: variant.name, updatedAt: now() };
  if (existing) Object.assign(existing, row);
  else list.push(row);
}

