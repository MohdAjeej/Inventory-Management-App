/**
 * DEMO DATA PROVIDER. Implements the ApiClient interface over the in-memory demo store so the
 * dashboard can be previewed without a backend. Paths, shapes, permission checks and error codes
 * follow the real API; nothing is persisted (a page reload resets the data).
 */
import { ApiError, type ApiClient, type Query, type RequestOptions } from "@/lib/api/client";
import type * as T from "@/lib/api/types";
import { ROLE_DEFAULTS } from "@/lib/permissions";
import {
  addSalePayment,
  applyMovement,
  available,
  balKey,
  cancelPurchase,
  cancelSale,
  createStore,
  invoiceLine,
  invoiceTotals,
  recordPurchase,
  recordSale,
  round2,
  sellerCard,
  statusOf,
  threshold,
  totals,
  transfer,
  unitOf,
  type DemoProduct,
  type DemoState,
} from "./store";
import { effectivePermissions, parityRoutes, rememberMapping } from "./parity";

let store: DemoState | null = null;
function db(): DemoState {
  if (!store) store = createStore();
  return store;
}

const ROLE_ACTOR: Record<T.Role, { id: number; name: string }> = {
  ADMIN: { id: 1, name: "Demo Admin" },
  INVENTORY_MANAGER: { id: 2, name: "Asha Rao" },
  STAFF: { id: 3, name: "Vikram Singh" },
};

export function demoMe(role: T.Role): T.Me {
  const actor = ROLE_ACTOR[role];
  return {
    userId: actor.id,
    fullName: actor.name,
    email: `${actor.name.split(" ")[0].toLowerCase()}@demo.example`,
    membershipId: actor.id,
    businessId: 900,
    businessName: "Demo Wholesale Co.",
    role,
    permissions: ROLE_DEFAULTS[role],
    memberships: [{ membershipId: actor.id, businessId: 900, businessName: "Demo Wholesale Co.", role, businessType: "Wholesaler" }],
  };
}

// ------------------------------------------------------------------ views

function canSeeCost(perms: T.Permission[]) {
  return perms.some((p) => p === "ANALYTICS_VIEW" || p === "PURCHASE_MANAGE" || p === "PRODUCT_EDIT");
}

function categoryName(s: DemoState, id?: number) {
  return s.categories.find((c) => c.id === id)?.name;
}

function categoryPath(s: DemoState, id?: number): string | undefined {
  const c = s.categories.find((x) => x.id === id);
  if (!c) return undefined;
  const parent = c.parentId ? categoryPath(s, c.parentId) : undefined;
  return parent ? `${parent} / ${c.name}` : c.name;
}

function productTotals(s: DemoState, p: DemoProduct): T.StockTotals {
  const t = { onHand: 0, reserved: 0, damaged: 0, available: 0 };
  for (const v of p.variants) {
    const x = totals(s, v.id);
    t.onHand += x.onHand;
    t.reserved += x.reserved;
    t.damaged += x.damaged;
    t.available += x.available;
  }
  return t;
}

function summary(s: DemoState, p: DemoProduct, cost: boolean): T.ProductSummary {
  const stock = productTotals(s, p);
  const min = threshold(s, p);
  const v = p.variants[0];
  return {
    id: p.id,
    name: p.name,
    brand: p.brand,
    categoryId: p.categoryId,
    categoryName: categoryName(s, p.categoryId),
    unitCode: unitOf(s, p).code,
    sku: v?.sku,
    barcode: v?.barcode,
    stock,
    effectiveMinStock: min,
    status: statusOf(stock.available, min),
    variantCount: p.variants.length,
    archived: p.archived,
    updatedAt: p.updatedAt,
    salePrice: p.salePrice,
    purchasePrice: cost ? p.purchasePrice : undefined,
  };
}

function detail(s: DemoState, p: DemoProduct, cost: boolean): T.ProductDetail {
  const stock = productTotals(s, p);
  const min = threshold(s, p);
  const unitCost = p.averageCost ?? p.purchasePrice;
  const balances: T.Balance[] = [];
  for (const v of p.variants)
    for (const w of s.warehouses) {
      const b = s.balances.get(balKey(v.id, w.id));
      if (!b) continue;
      balances.push({ warehouseId: w.id, warehouseName: w.name, variantId: v.id, variantName: v.name, ...b, available: b.onHand - b.reserved - b.damaged });
    }
  return {
    id: p.id,
    name: p.name,
    brand: p.brand,
    description: p.description,
    categoryId: p.categoryId,
    categoryName: categoryName(s, p.categoryId),
    categoryPath: categoryPath(s, p.categoryId),
    baseUnit: unitOf(s, p),
    minStock: p.minStock,
    effectiveMinStock: min,
    attributes: {},
    trackBatches: false,
    trackSerials: false,
    archived: p.archived,
    stock,
    status: statusOf(stock.available, min),
    variants: p.variants.map((v) => {
      const t = totals(s, v.id);
      const vm = threshold(s, p, v);
      return { ...v, effectiveMinStock: vm, stock: t, status: statusOf(t.available, vm) };
    }),
    images: [],
    balances,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
    salePrice: p.salePrice,
    taxRate: p.taxRate,
    supplierId: p.supplierId,
    supplierName: s.suppliers.find((x) => x.id === p.supplierId)?.name,
    ...(cost
      ? {
          purchasePrice: p.purchasePrice,
          averageCost: p.averageCost,
          pricing:
            unitCost !== undefined
              ? {
                  unitCost: round2(unitCost),
                  costSource: p.averageCost !== undefined ? ("AVERAGE" as const) : ("PURCHASE_PRICE" as const),
                  profitPerUnit: p.salePrice !== undefined ? round2(p.salePrice - unitCost) : undefined,
                  marginPercent: p.salePrice ? round2(((p.salePrice - unitCost) / p.salePrice) * 100) : undefined,
                  stockValueAtCost: round2(stock.available * unitCost),
                  stockValueAtSellingPrice: p.salePrice !== undefined ? round2(stock.available * p.salePrice) : undefined,
                  potentialProfit: p.salePrice !== undefined ? round2(stock.available * (p.salePrice - unitCost)) : undefined,
                }
              : undefined,
        }
      : {}),
  };
}

function stockItem(s: DemoState, p: DemoProduct): T.StockItem {
  const st = productTotals(s, p);
  const min = threshold(s, p);
  return { productId: p.id, productName: p.name, unit: unitOf(s, p).code, available: st.available, minStock: min, status: statusOf(st.available, min), updatedAt: p.updatedAt };
}

function inventoryValue(s: DemoState): T.InventoryValue {
  let cost = 0;
  let sell = 0;
  let noCost = 0;
  let noPrice = 0;
  for (const p of s.products.filter((x) => !x.archived)) {
    const a = Math.max(productTotals(s, p).available, 0);
    const c = p.averageCost ?? p.purchasePrice;
    if (c === undefined) noCost++;
    else cost += a * c;
    if (p.salePrice === undefined) noPrice++;
    else sell += a * p.salePrice;
  }
  return { valueAtCost: round2(cost), valueAtSellingPrice: round2(sell), potentialProfit: round2(sell - cost), productsWithoutCost: noCost, productsWithoutPrice: noPrice };
}

function page<X>(items: X[], q: Query, defaultSize = 20): T.Page<X> {
  const size = Math.min(Number(q.size ?? defaultSize) || defaultSize, 200);
  const pg = Number(q.page ?? 0) || 0;
  return {
    content: items.slice(pg * size, pg * size + size),
    page: pg,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

function contains(hay: (string | undefined)[], q: unknown) {
  if (!q) return true;
  const needle = String(q).toLowerCase();
  return hay.some((h) => h?.toLowerCase().includes(needle));
}

// ------------------------------------------------------------------ analytics

function inRange(iso: string, from: string, to: string) {
  const d = new Date(iso).toLocaleDateString("en-CA");
  return d >= from && d <= to;
}

function shiftDate(isoDate: string, days: number) {
  const d = new Date(`${isoDate}T00:00:00`);
  d.setDate(d.getDate() + days);
  return d.toLocaleDateString("en-CA");
}

function period(q: Query): T.Period {
  const to = String(q.to ?? new Date().toLocaleDateString("en-CA"));
  const from = String(q.from ?? shiftDate(to, -29));
  const days = Math.round((new Date(`${to}T00:00:00`).getTime() - new Date(`${from}T00:00:00`).getTime()) / 86400000) + 1;
  return { from, to, days, previousFrom: shiftDate(from, -days), previousTo: shiftDate(from, -1), timezone: "Asia/Kolkata", currencyCode: "INR" };
}

function salesIn(s: DemoState, from: string, to: string) {
  return s.sales.filter((x) => x.status === "COMPLETED" && inRange(x.soldAt, from, to));
}

function metric(value: number, previous: number): T.Metric {
  return { value: round2(value), previous: round2(previous), growthPercent: previous > 0 ? round2(((value - previous) / previous) * 100) : undefined };
}

function aggregate(sales: T.Sale[]) {
  let revenue = 0;
  let units = 0;
  let profit = 0;
  let gross = 0;
  let discounts = 0;
  let tax = 0;
  let cost = 0;
  let missing = 0;
  for (const sale of sales)
    for (const it of sale.items) {
      revenue += it.netAmount;
      units += it.quantity;
      gross += it.grossAmount;
      discounts += it.discount;
      tax += it.taxAmount;
      if (it.costAmount === undefined) missing++;
      else {
        cost += it.costAmount;
        profit += it.netAmount - it.costAmount;
      }
    }
  return { revenue, units, profit, gross, discounts, tax, cost, missing, count: sales.length };
}

function productPerformance(s: DemoState, q: Query): T.ProductPerformance[] {
  const p = period(q);
  const cur = salesIn(s, p.from, p.to);
  const prev = salesIn(s, p.previousFrom, p.previousTo);
  const rows = s.products
    .filter((x) => !x.archived && contains([x.name, x.variants[0]?.sku], q.q))
    .filter((x) => !q.categoryId || x.categoryId === Number(q.categoryId))
    .map((prod) => {
      const lines = cur.flatMap((x) => x.items.filter((i) => i.productId === prod.id).map((i) => ({ i, at: x.soldAt })));
      const prevUnits = prev.flatMap((x) => x.items).filter((i) => i.productId === prod.id).reduce((a, i) => a + i.quantity, 0);
      const unitsSold = lines.reduce((a, l) => a + l.i.quantity, 0);
      const revenue = round2(lines.reduce((a, l) => a + l.i.netAmount, 0));
      const profit = round2(lines.reduce((a, l) => a + (l.i.grossProfit ?? 0), 0));
      const st = productTotals(s, prod);
      const growth = prevUnits > 0 ? round2(((unitsSold - prevUnits) / prevUnits) * 100) : undefined;
      const performance: T.Performance =
        unitsSold === 0 && prevUnits === 0 ? "NO_DATA" : prevUnits === 0 ? "NEW" : (growth ?? 0) > 10 ? "GROWING" : (growth ?? 0) < -10 ? "DECLINING" : "STABLE";
      return {
        rank: 0,
        productId: prod.id,
        name: prod.name,
        sku: prod.variants[0]?.sku,
        categoryName: categoryName(s, prod.categoryId),
        unit: unitOf(s, prod).code,
        unitsSold,
        revenue,
        grossProfit: profit,
        marginPercent: revenue > 0 ? round2((profit / revenue) * 100) : undefined,
        salesCount: new Set(lines.map((l) => l.at)).size,
        averageDailySales: round2(unitsSold / p.days),
        currentStock: st.available,
        stockStatus: statusOf(st.available, threshold(s, prod)),
        previousUnits: prevUnits,
        growthPercent: growth,
        performance,
        labels: [] as T.ProductLabel[],
        lastSoldAt: lines[0]?.at,
        archived: false,
      };
    });
  const sort = String(q.sort ?? "units");
  const key: Record<string, (r: T.ProductPerformance) => number | string> = {
    units: (r) => -r.unitsSold,
    revenue: (r) => -r.revenue,
    profit: (r) => -(r.grossProfit ?? 0),
    growth: (r) => -(r.growthPercent ?? -Infinity),
    name: (r) => r.name,
  };
  const f = key[sort] ?? key.units;
  rows.sort((a, b) => (f(a) < f(b) ? -1 : f(a) > f(b) ? 1 : 0));
  const byUnits = [...rows].sort((a, b) => b.unitsSold - a.unitsSold);
  const sold = byUnits.filter((r) => r.unitsSold > 0);
  rows.forEach((r, i) => {
    r.rank = i + 1;
    if (sold[0] && r.productId === sold[0].productId) r.labels.push("TOP_SELLER");
    if (sold.slice(0, 3).some((x) => x.productId === r.productId)) r.labels.push("FAST_MOVING");
    if (r.unitsSold === 0) r.labels.push("NO_SALES");
    else if (sold.slice(-3).some((x) => x.productId === r.productId)) r.labels.push("SLOW_MOVING");
  });
  return rows;
}

// ------------------------------------------------------------------ router

type Handler = (ctx: { m: RegExpMatchArray; q: Query; body: any; me: T.Me; s: DemoState }) => unknown; // eslint-disable-line @typescript-eslint/no-explicit-any

function forbid(me: T.Me, ...perms: T.Permission[]) {
  if (!perms.some((p) => me.permissions.includes(p))) {
    throw new ApiError({ status: 403, code: "FORBIDDEN", message: "You don't have permission to do this." });
  }
}

function notFound(what = "Record"): never {
  throw new ApiError({ status: 404, code: "NOT_FOUND", message: `${what} not found` });
}

function required(fields: Record<string, unknown>) {
  const errors: Record<string, string> = {};
  for (const [k, v] of Object.entries(fields)) if (v === undefined || v === null || v === "") errors[k] = "This field is required";
  if (Object.keys(errors).length) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Validation failed", fieldErrors: errors });
}

const now = () => new Date().toISOString();

const routes: [string, RegExp, Handler][] = [
  ["GET", /^\/auth\/me$/, ({ me }) => me],
  ["PUT", /^\/auth\/me$/, ({ me, body }) => ({ ...me, fullName: body.fullName, phone: body.phone })],
  ["POST", /^\/auth\/change-password$/, () => {
    throw new ApiError({ status: 400, code: "DEMO_ONLY", message: "Passwords can't be changed in the demo." });
  }],

  ["GET", /^\/dashboard$/, ({ s, me }) => {
    const active = s.products.filter((p) => !p.archived);
    const items = active.map((p) => stockItem(s, p));
    const month = new Date().toLocaleDateString("en-CA").slice(0, 8) + "01";
    const today = new Date().toLocaleDateString("en-CA");
    const snap = (from: string): T.SalesSnapshot => {
      const a = aggregate(salesIn(s, from, today));
      return { sales: a.count, units: a.units, revenue: round2(a.revenue), grossProfit: a.missing ? undefined : round2(a.profit), currencyCode: "INR" };
    };
    const perf = productPerformance(s, { from: month, to: today, sort: "units" });
    const sold = perf.filter((r) => r.unitsSold > 0);
    const ref = (r?: T.ProductPerformance): T.ProductRef | undefined => (r ? { productId: r.productId, name: r.name, unit: r.unit, units: r.unitsSold, revenue: r.revenue } : undefined);
    const cats = new Map<string, T.Bucket>();
    for (const p of active) {
      const top = s.categories.find((c) => c.id === p.categoryId);
      const root = top?.parentId ? s.categories.find((c) => c.id === top.parentId) : top;
      const name = root?.name ?? "Uncategorized";
      const b = cats.get(name) ?? { categoryId: root?.id, categoryName: name, productCount: 0, quantity: 0 };
      b.productCount++;
      b.quantity += productTotals(s, p).onHand;
      cats.set(name, b);
    }
    const dash: T.Dashboard = {
      activeProducts: active.length,
      totalQuantity: active.reduce((a, p) => a + productTotals(s, p).onHand, 0),
      lowStockCount: items.filter((i) => i.status === "LOW_STOCK").length,
      outOfStockCount: items.filter((i) => i.status === "OUT_OF_STOCK").length,
      lowStockItems: items.filter((i) => i.status === "LOW_STOCK"),
      outOfStockItems: items.filter((i) => i.status === "OUT_OF_STOCK"),
      recentMovements: s.movements.slice(0, 8),
      categories: [...cats.values()].sort((a, b) => b.quantity - a.quantity),
      warehouses: s.warehouses.map((w) => ({
        warehouseId: w.id,
        warehouseName: w.name,
        productCount: active.filter((p) => (s.balances.get(balKey(p.id, w.id))?.onHand ?? 0) > 0).length,
        quantity: active.reduce((a, p) => a + (s.balances.get(balKey(p.id, w.id))?.onHand ?? 0), 0),
      })),
      recentlyUpdated: [...items].sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? "")).slice(0, 5),
      mostStocked: [...items].sort((a, b) => b.available - a.available).slice(0, 5),
      unreadNotifications: s.notifications.filter((n) => !n.read).length,
    };
    if (me.permissions.includes("ANALYTICS_VIEW")) {
      dash.sales = {
        today: snap(today),
        month: snap(month),
        monthBestSeller: ref(sold[0]),
        monthLowestSeller: ref(sold[sold.length - 1]),
        monthProductsWithNoSales: perf.filter((r) => r.unitsSold === 0).length,
        inventory: inventoryValue(s),
      };
    }
    return dash;
  }],

  ["GET", /^\/business$/, ({ s }) => s.business],
  ["PUT", /^\/business$/, ({ s, me, body }) => {
    forbid(me, "BUSINESS_SETTINGS");
    required({ name: body.name });
    Object.assign(s.business, body);
    return s.business;
  }],
  ["GET", /^\/business\/settings$/, ({ s }) => s.settings],
  ["PUT", /^\/business\/settings$/, ({ s, me, body }) => {
    forbid(me, "BUSINESS_SETTINGS");
    Object.assign(s.settings, body);
    return s.settings;
  }],

  ["GET", /^\/units$/, ({ s }) => s.units],
  ["GET", /^\/attributes$/, ({ s }) => s.attributes],
  ["GET", /^\/categories$/, ({ s }) => {
    const build = (parentId?: number, depth = 0): T.Category[] =>
      s.categories
        .filter((c) => c.parentId === parentId)
        .map((c) => ({
          ...c,
          path: categoryPath(s, c.id) ?? c.name,
          depth,
          productCount: s.products.filter((p) => p.categoryId === c.id && !p.archived).length,
          children: build(c.id, depth + 1),
        }));
    return build(undefined);
  }],
  ["GET", /^\/warehouses$/, ({ s }) =>
    s.warehouses.map((w) => {
      let productCount = 0;
      let totalQuantity = 0;
      for (const p of s.products)
        for (const v of p.variants) {
          const onHand = s.balances.get(balKey(v.id, w.id))?.onHand ?? 0;
          if (onHand > 0) productCount++;
          totalQuantity += onHand;
        }
      return { ...w, productCount, totalQuantity };
    })],

  ["GET", /^\/products$/, ({ s, q, me }) => {
    forbid(me, "PRODUCT_VIEW");
    const archived = String(q.archived) === "true";
    let rows = s.products
      .filter((p) => p.archived === archived)
      .filter((p) => contains([p.name, p.brand, ...p.variants.map((v) => v.sku), ...p.variants.map((v) => v.barcode)], q.q))
      .filter((p) => {
        if (!q.categoryId) return true;
        const id = Number(q.categoryId);
        const c = s.categories.find((x) => x.id === p.categoryId);
        return p.categoryId === id || c?.parentId === id;
      })
      .map((p) => summary(s, p, canSeeCost(me.permissions)));
    if (q.status) rows = rows.filter((r) => r.status === q.status);
    const sort = String(q.sort ?? "name");
    rows.sort((a, b) => (sort === "name" ? a.name.localeCompare(b.name) : b.updatedAt.localeCompare(a.updatedAt)));
    return page(rows, q, 20);
  }],
  ["GET", /^\/products\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "PRODUCT_VIEW");
    const p = s.products.find((x) => x.id === Number(m[1])) ?? notFound("Product");
    return detail(s, p, canSeeCost(me.permissions));
  }],
  ["POST", /^\/products$/, ({ s, me, body }) => {
    forbid(me, "PRODUCT_CREATE");
    const req = body as T.ProductRequest;
    required({ name: req.name?.trim() });
    if (req.sku && s.products.some((p) => p.variants.some((v) => v.sku?.toLowerCase() === req.sku?.toLowerCase()))) {
      throw new ApiError({ status: 409, code: "CONFLICT", message: "Another product already uses this SKU", fieldErrors: { sku: "SKU already in use" } });
    }
    const id = s.seq.product++;
    const at = now();
    const p: DemoProduct = {
      id,
      name: req.name.trim(),
      brand: req.brand || undefined,
      categoryId: req.categoryId ?? undefined,
      unitId: req.baseUnitId ?? 1,
      description: req.description || undefined,
      salePrice: req.salePrice ?? undefined,
      purchasePrice: req.purchasePrice ?? undefined,
      averageCost: undefined,
      taxRate: req.taxRate ?? 0,
      minStock: req.minStock ?? undefined,
      supplierId: req.supplierId ?? undefined,
      archived: false,
      createdAt: at,
      updatedAt: at,
      variants: [{ id, productId: id, name: req.name.trim(), sku: req.sku || undefined, barcode: req.barcode || undefined, isDefault: true, attributes: {}, archived: false }],
    };
    s.products.push(p);
    if (req.openingStock?.quantity) {
      forbid(me, "STOCK_ADD");
      applyMovement(s, { type: "OPENING", variantId: id, warehouseId: req.openingStock.warehouseId ?? 1, quantity: req.openingStock.quantity, reason: "Opening stock", actor: ROLE_ACTOR[me.role], at });
    }
    return detail(s, p, canSeeCost(me.permissions));
  }],
  ["POST", /^\/products\/(\d+)\/(archive|restore)$/, ({ s, m, me }) => {
    forbid(me, "PRODUCT_ARCHIVE");
    const p = s.products.find((x) => x.id === Number(m[1])) ?? notFound("Product");
    p.archived = m[2] === "archive";
    p.updatedAt = now();
    return detail(s, p, canSeeCost(me.permissions));
  }],

  ["POST", /^\/stock\/operations$/, ({ s, me, body }) => {
    const req = body as T.StockOperationRequest;
    forbid(me, req.type === "ADD" ? "STOCK_ADD" : req.type === "REDUCE" ? "STOCK_REDUCE" : "STOCK_ADJUST");
    required({ variantId: req.variantId, quantity: req.quantity });
    if (req.type === "ADJUST" && !req.reason?.trim()) {
      throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "A reason is required for adjustments", fieldErrors: { reason: "A reason is required" } });
    }
    const wh = req.warehouseId ?? 1;
    let qty = req.quantity;
    if (req.type === "ADJUST") {
      // ADJUST sends the corrected on-hand quantity; the movement stores the difference.
      qty = req.quantity - (s.balances.get(balKey(req.variantId, wh))?.onHand ?? 0);
      if (qty === 0) throw new ApiError({ status: 400, code: "BAD_REQUEST", message: "Stock already matches; nothing to adjust." });
    }
    const mv = applyMovement(s, { type: req.type, variantId: req.variantId, warehouseId: wh, quantity: qty, reason: req.reason, notes: req.notes, actor: ROLE_ACTOR[me.role], at: now() });
    return { movements: [mv], balances: [] };
  }],
  ["POST", /^\/stock\/transfers$/, ({ s, me, body }) => {
    forbid(me, "STOCK_TRANSFER");
    const req = body as T.TransferRequest;
    required({ variantId: req.variantId, fromWarehouseId: req.fromWarehouseId, toWarehouseId: req.toWarehouseId, quantity: req.quantity });
    return { movements: transfer(s, req, ROLE_ACTOR[me.role], now()), balances: [] };
  }],
  ["GET", /^\/stock\/movements$/, ({ s, q, me }) => {
    forbid(me, "PRODUCT_VIEW");
    const types = ([] as (string | number)[]).concat((q.types as string[] | undefined) ?? []).map(String);
    const rows = s.movements.filter(
      (mv) =>
        (!q.productId || mv.productId === Number(q.productId)) &&
        (!q.warehouseId || mv.warehouseId === Number(q.warehouseId)) &&
        (!types.length || types.includes(mv.type)) &&
        (!q.from || mv.createdAt >= String(q.from)) &&
        (!q.to || mv.createdAt <= String(q.to)),
    );
    return page(rows, q, 30);
  }],

  ["GET", /^\/reports\/(inventory|low-stock)$/, ({ s, q, m, me }) => {
    forbid(me, "REPORT_VIEW");
    const rows: T.InventoryRow[] = [];
    for (const p of s.products.filter((x) => !x.archived && contains([x.name, x.variants[0]?.sku], q.q)))
      for (const v of p.variants) {
        const t = totals(s, v.id);
        const min = threshold(s, p, v);
        const status = statusOf(t.available, min);
        if (m[1] === "low-stock" && status === "IN_STOCK") continue;
        if (q.status && q.status !== status) continue;
        rows.push({ productId: p.id, variantId: v.id, productName: p.name, variantName: v.name, sku: v.sku, category: categoryName(s, p.categoryId), unit: unitOf(s, p).code, ...t, minStock: min, status });
      }
    return page(rows, q, 50);
  }],
  ["GET", /^\/reports\/movement-summary$/, ({ s, me }) => {
    forbid(me, "REPORT_VIEW");
    const from = new Date(Date.now() - 30 * 86400000).toISOString();
    const recent = s.movements.filter((mv) => mv.createdAt >= from && mv.bucket === "ON_HAND");
    const byType = new Map<T.MovementType, { type: T.MovementType; count: number; quantity: number }>();
    for (const mv of recent) {
      const e = byType.get(mv.type) ?? { type: mv.type, count: 0, quantity: 0 };
      e.count++;
      e.quantity += mv.quantity;
      byType.set(mv.type, e);
    }
    const out = new Map<number, number>();
    for (const mv of recent) if (mv.quantity < 0 && mv.type !== "TRANSFER_OUT") out.set(mv.productId, (out.get(mv.productId) ?? 0) + -mv.quantity);
    const rows = s.products.filter((p) => !p.archived).map((p) => ({ productId: p.id, productName: p.name, unit: unitOf(s, p).code, quantityOut: out.get(p.id) ?? 0, available: productTotals(s, p).available }));
    rows.sort((a, b) => b.quantityOut - a.quantityOut);
    return {
      from,
      to: now(),
      totalIn: recent.filter((mv) => mv.quantity > 0).reduce((a, mv) => a + mv.quantity, 0),
      totalOut: -recent.filter((mv) => mv.quantity < 0).reduce((a, mv) => a + mv.quantity, 0),
      movementCount: recent.length,
      byType: [...byType.values()],
      fastMoving: rows.slice(0, 5),
      slowMoving: [...rows].reverse().slice(0, 5),
    } satisfies T.MovementSummary;
  }],

  ["GET", /^\/analytics\/overview$/, ({ s, q, me }) => {
    forbid(me, "ANALYTICS_VIEW");
    const p = period(q);
    const cur = aggregate(salesIn(s, p.from, p.to));
    const prev = aggregate(salesIn(s, p.previousFrom, p.previousTo));
    const perf = productPerformance(s, q).sort((a, b) => b.unitsSold - a.unitsSold);
    const sold = perf.filter((r) => r.unitsSold > 0);
    const purchases = s.purchases.filter((x) => x.status === "RECEIVED" && inRange(x.purchasedAt, p.from, p.to));
    const prevPurchases = s.purchases.filter((x) => x.status === "RECEIVED" && inRange(x.purchasedAt, p.previousFrom, p.previousTo));
    const ref = (r?: T.ProductPerformance) => (r ? { productId: r.productId, name: r.name, unit: r.unit, units: r.unitsSold, revenue: r.revenue } : undefined);
    return {
      period: p,
      sales: metric(cur.count, prev.count),
      units: metric(cur.units, prev.units),
      revenue: metric(cur.revenue, prev.revenue),
      grossProfit: metric(cur.profit, prev.profit),
      financials: {
        grossSales: round2(cur.gross),
        discounts: round2(cur.discounts),
        returns: 0,
        netSales: round2(cur.revenue),
        taxCollected: round2(cur.tax),
        costOfGoodsSold: cur.missing ? undefined : round2(cur.cost),
        grossProfit: cur.missing ? undefined : round2(cur.profit),
        marginPercent: cur.revenue > 0 && !cur.missing ? round2((cur.profit / cur.revenue) * 100) : undefined,
        profitAvailable: cur.missing === 0,
        linesWithoutCost: cur.missing,
      },
      averageDailyUnits: round2(cur.units / p.days),
      averageDailyRevenue: round2(cur.revenue / p.days),
      bestSeller: ref(sold[0]),
      lowestSeller: ref(sold[sold.length - 1]),
      productsWithNoSales: perf.length - sold.length,
      productsSold: sold.length,
      activeProducts: perf.length,
      inventory: inventoryValue(s),
      purchases: {
        purchases: purchases.length,
        unitsReceived: purchases.flatMap((x) => x.items).reduce((a, i) => a + i.quantity, 0),
        totalSpent: round2(purchases.reduce((a, x) => a + x.total, 0)),
        previousTotalSpent: round2(prevPurchases.reduce((a, x) => a + x.total, 0)),
      },
    } satisfies T.AnalyticsOverview;
  }],
  ["GET", /^\/analytics\/products$/, ({ s, q, me }) => {
    forbid(me, "ANALYTICS_VIEW");
    return { period: period(q), products: productPerformance(s, q) };
  }],
  ["GET", /^\/analytics\/trend$/, ({ s, q, me }) => {
    forbid(me, "ANALYTICS_VIEW");
    const p = period(q);
    const points: T.TrendPoint[] = [];
    for (let d = p.from; d <= p.to; d = shiftDate(d, 1)) {
      const a = aggregate(salesIn(s, d, d));
      points.push({ start: d, units: a.units, revenue: round2(a.revenue), grossProfit: a.missing ? undefined : round2(a.profit), sales: a.count });
    }
    return { period: p, granularity: "day", points } satisfies T.Trend;
  }],
  ["GET", /^\/analytics\/categories$/, ({ s, q, me }) => {
    forbid(me, "ANALYTICS_VIEW");
    const perf = productPerformance(s, q);
    const map = new Map<string, T.CategoryPerformance>();
    for (const r of perf) {
      const name = r.categoryName ?? "Uncategorized";
      const c = map.get(name) ?? { categoryName: name, units: 0, revenue: 0, grossProfit: 0, productsSold: 0, previousUnits: 0 };
      c.units += r.unitsSold;
      c.revenue = round2(c.revenue + r.revenue);
      c.grossProfit = round2((c.grossProfit ?? 0) + (r.grossProfit ?? 0));
      if (r.unitsSold > 0) c.productsSold++;
      c.previousUnits += r.previousUnits;
      map.set(name, c);
    }
    const categories = [...map.values()].map((c) => ({ ...c, growthPercent: c.previousUnits > 0 ? round2(((c.units - c.previousUnits) / c.previousUnits) * 100) : undefined }));
    return { period: period(q), categories: categories.sort((a, b) => b.revenue - a.revenue) };
  }],
  ["GET", /^\/analytics\/insights$/, ({ s, q, me }) => {
    forbid(me, "ANALYTICS_VIEW");
    const perf = productPerformance(s, q).sort((a, b) => b.unitsSold - a.unitsSold);
    const insights: T.Insight[] = [];
    const best = perf.find((r) => r.unitsSold > 0);
    if (best) insights.push({ kind: "BEST_SELLER", severity: "GOOD", title: "Best seller", message: `${best.name} sold ${best.unitsSold} ${best.unit} in this period — your best-selling product.`, productId: best.productId, action: "VIEW_PRODUCT" });
    for (const r of perf.filter((x) => x.unitsSold > 0 && x.stockStatus !== "IN_STOCK").slice(0, 2)) {
      insights.push({ kind: "REPLENISH", severity: "WARNING", title: "Restock soon", message: `${r.name} sells about ${r.averageDailySales} ${r.unit}/day and only ${r.currentStock} ${r.unit} are available.`, productId: r.productId, action: "ADD_STOCK" });
    }
    const none = perf.filter((r) => r.unitsSold === 0);
    if (none.length) insights.push({ kind: "NO_SALES", severity: "INFO", title: "Products with no sales", message: `${none.length} product${none.length > 1 ? "s" : ""} had no sales in this period, e.g. ${none[0].name}.`, productId: none[0].productId, action: "VIEW_PRODUCT" });
    return { period: period(q), insights };
  }],

  ["GET", /^\/sales$/, ({ s, q, me }) => {
    forbid(me, "SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW");
    const rows = s.sales
      .filter((x) => contains([x.invoiceNumber, x.customerName, x.customerPhone, String(x.id)], q.q))
      .filter((x) => !q.paymentStatus || x.paymentStatus === q.paymentStatus)
      .filter((x) => !q.status || x.status === q.status)
      .filter((x) => !q.customerId || x.customerId === Number(q.customerId))
      .filter((x) => (!q.from || x.soldAt.slice(0, 10) >= String(q.from)) && (!q.to || x.soldAt.slice(0, 10) <= String(q.to)))
      .map<T.SaleSummary>((x) => ({
        id: x.id,
        invoiceNumber: x.invoiceNumber,
        soldAt: x.soldAt,
        customerName: x.customerName,
        customerPhone: x.customerPhone,
        itemCount: x.items.length,
        totalQuantity: x.items.reduce((a, i) => a + i.quantity, 0),
        products: x.items.map((i) => i.productName).join(", "),
        total: x.total,
        returnedTotal: x.returnedTotal,
        amountPaid: x.amountPaid,
        outstanding: x.outstanding,
        paymentStatus: x.paymentStatus,
        status: x.status,
      }));
    return page(rows, q, 30);
  }],
  ["GET", /^\/sales\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW");
    const sale = s.sales.find((x) => x.id === Number(m[1])) ?? notFound("Sale");
    if (canSeeCost(me.permissions)) return sale;
    const { costTotal: _c, grossProfit: _g, ...rest } = sale; // eslint-disable-line @typescript-eslint/no-unused-vars
    return { ...rest, items: sale.items.map(({ unitCost: _u, costAmount: _a, grossProfit: _p, ...i }) => i) }; // eslint-disable-line @typescript-eslint/no-unused-vars
  }],
  ["POST", /^\/sales$/, ({ s, me, body }) => {
    forbid(me, "SALE_RECORD");
    required({ clientRequestId: body.clientRequestId });
    return recordSale(s, body as T.SaleRequest, { at: now(), actor: ROLE_ACTOR[me.role] });
  }],
  ["POST", /^\/sales\/(\d+)\/payments$/, ({ s, m, me, body }) => {
    forbid(me, "SALE_RECORD");
    const sale = s.sales.find((x) => x.id === Number(m[1])) ?? notFound("Sale");
    addSalePayment(s, sale, Number(body.amount), body.method, body.reference, ROLE_ACTOR[me.role].name);
    return sale;
  }],
  ["POST", /^\/sales\/(\d+)\/cancel$/, ({ s, m, me, body }) => {
    forbid(me, "SALE_CANCEL");
    required({ reason: body.reason });
    const sale = s.sales.find((x) => x.id === Number(m[1])) ?? notFound("Sale");
    cancelSale(s, sale, body.reason, body.refundMethod ?? "CASH", ROLE_ACTOR[me.role]);
    return sale;
  }],

  ["GET", /^\/purchases$/, ({ s, q, me }) => {
    forbid(me, "PURCHASE_MANAGE");
    const rows = s.purchases
      .filter((x) => contains([x.purchaseNumber, x.referenceNumber, x.supplierName], q.q))
      .filter((x) => !q.paymentStatus || x.paymentStatus === q.paymentStatus)
      .filter((x) => !q.status || x.status === q.status)
      .map<T.PurchaseSummary>((x) => ({
        id: x.id,
        purchaseNumber: x.purchaseNumber,
        referenceNumber: x.referenceNumber,
        purchasedAt: x.purchasedAt,
        supplierName: x.supplierName,
        itemCount: x.items.length,
        totalQuantity: x.items.reduce((a, i) => a + i.quantity, 0),
        products: x.items.map((i) => i.productName).join(", "),
        total: x.total,
        amountPaid: x.amountPaid,
        outstanding: x.outstanding,
        paymentStatus: x.paymentStatus,
        status: x.status,
      }));
    return page(rows, q, 30);
  }],
  ["GET", /^\/purchases\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "PURCHASE_MANAGE");
    return s.purchases.find((x) => x.id === Number(m[1])) ?? notFound("Purchase");
  }],
  ["POST", /^\/purchases$/, ({ s, me, body }) => {
    forbid(me, "PURCHASE_MANAGE");
    return recordPurchase(s, body as T.PurchaseRequest, { at: now(), actor: ROLE_ACTOR[me.role] });
  }],
  ["POST", /^\/purchases\/(\d+)\/payments$/, ({ s, m, me, body }) => {
    forbid(me, "PURCHASE_MANAGE");
    const p = s.purchases.find((x) => x.id === Number(m[1])) ?? notFound("Purchase");
    if (p.status === "CANCELLED") throw new ApiError({ status: 409, code: "CONFLICT", message: "This purchase is cancelled" });
    const amount = Number(body.amount);
    if (amount > p.outstanding + 0.001) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Amount exceeds the outstanding balance", fieldErrors: { amount: "Amount exceeds the outstanding balance" } });
    p.payments.push({ id: s.seq.misc++, kind: "PURCHASE_PAYMENT", amount, method: body.method, reference: body.reference, paidAt: now(), recordedBy: ROLE_ACTOR[me.role].name });
    p.amountPaid = round2(p.amountPaid + amount);
    p.outstanding = round2(p.total - p.amountPaid);
    p.paymentStatus = p.outstanding <= 0 ? "PAID" : "PARTIALLY_PAID";
    return p;
  }],
  ["POST", /^\/purchases\/(\d+)\/cancel$/, ({ s, m, me, body }) => {
    forbid(me, "PURCHASE_MANAGE");
    required({ reason: body.reason });
    const p = s.purchases.find((x) => x.id === Number(m[1])) ?? notFound("Purchase");
    cancelPurchase(s, p, body.reason, ROLE_ACTOR[me.role]);
    return p;
  }],

  ["GET", /^\/(customers|suppliers)$/, ({ s, m, q, me }) => {
    if (m[1] === "customers") forbid(me, "SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW");
    else forbid(me, "PURCHASE_MANAGE", "PRODUCT_EDIT", "PRODUCT_CREATE");
    const list = m[1] === "customers" ? s.customers : s.suppliers;
    return list.filter((x) => (String(q.includeArchived) === "true" || !x.archived) && contains([x.name, x.phone, x.email], q.q));
  }],
  ["POST", /^\/(customers|suppliers)$/, ({ s, m, me, body }) => {
    forbid(me, m[1] === "customers" ? "SALE_RECORD" : "PURCHASE_MANAGE");
    required({ name: body.name?.trim() });
    const party: T.Party = { id: s.seq.party++, ...body, archived: false, createdAt: now() };
    (m[1] === "customers" ? s.customers : s.suppliers).push(party);
    return party;
  }],
  ["PUT", /^\/(customers|suppliers)\/(\d+)$/, ({ s, m, me, body }) => {
    forbid(me, m[1] === "customers" ? "SALE_RECORD" : "PURCHASE_MANAGE");
    required({ name: body.name?.trim() });
    const party = (m[1] === "customers" ? s.customers : s.suppliers).find((x) => x.id === Number(m[2])) ?? notFound();
    Object.assign(party, body);
    return party;
  }],
  ["POST", /^\/(customers|suppliers)\/(\d+)\/(archive|restore)$/, ({ s, m, me }) => {
    forbid(me, m[1] === "customers" ? "SALE_RECORD" : "PURCHASE_MANAGE");
    const party = (m[1] === "customers" ? s.customers : s.suppliers).find((x) => x.id === Number(m[2])) ?? notFound();
    party.archived = m[3] === "archive";
    return party;
  }],

  ["GET", /^\/network\/me$/, ({ s }) => ({ businessCode: s.business.businessCode, name: s.business.name, discoverable: true })],
  ["GET", /^\/network\/connections$/, ({ s, me }) => {
    forbid(me, "SALE_RECORD", "PURCHASE_MANAGE", "BUSINESS_SETTINGS");
    return s.connections;
  }],

  ["GET", /^\/trade\/summary$/, ({ s }) => ({
    pendingApprovals: s.invoices.filter((i) => i.role === "BUYER" && i.status === "PENDING_APPROVAL").length,
    awaitingBuyer: s.invoices.filter((i) => i.role === "SELLER" && i.status === "PENDING_APPROVAL").length,
    drafts: s.invoices.filter((i) => i.status === "DRAFT").length,
    incomingConnections: 0,
    returnsToReview: 0,
    connectedBusinesses: s.connections.filter((c) => c.status === "ACCEPTED").length,
  })],
  ["GET", /^\/trade\/invoices$/, ({ s, q, me }) => {
    const direction = String(q.direction ?? "SALES");
    forbid(me, direction === "SALES" ? "SALE_RECORD" : "PURCHASE_MANAGE");
    const role = direction === "SALES" ? "SELLER" : "BUYER";
    const rows = s.invoices
      .filter((i) => i.role === role && (!q.status || i.status === q.status))
      .filter((i) => contains([i.invoiceNumber, i.buyer.name, i.seller.name], q.q))
      .map<T.TradeInvoiceSummary>((i) => ({
        id: i.id,
        invoiceNumber: i.invoiceNumber,
        status: i.status,
        role: i.role,
        counterparty: i.role === "SELLER" ? i.buyer : i.seller,
        invoiceDate: i.invoiceDate,
        lineCount: i.lines.length,
        total: i.total,
        currencyCode: i.currencyCode,
        createdAt: i.createdAt,
        sentAt: i.sentAt,
        respondedAt: i.respondedAt,
        returnPending: false,
      }));
    return page(rows, q, 30);
  }],
  ["GET", /^\/trade\/invoices\/(\d+)$/, ({ s, m, me }) => {
    const inv = s.invoices.find((i) => i.id === Number(m[1])) ?? notFound("Invoice");
    forbid(me, inv.role === "SELLER" ? "SALE_RECORD" : "PURCHASE_MANAGE");
    return inv;
  }],
  ["POST", /^\/trade\/invoices$/, ({ s, me, body }) => {
    forbid(me, "SALE_RECORD");
    const req = body as T.TradeInvoiceRequest;
    required({ buyerBusinessId: req.buyerBusinessId });
    if (!req.lines?.length) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Add at least one product", fieldErrors: { lines: "Add at least one product" } });
    const buyer = s.connections.find((c) => c.business.id === req.buyerBusinessId && c.status === "ACCEPTED")?.business;
    if (!buyer) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "You can only invoice connected businesses" });
    const lines = req.lines.map((l, i) => invoiceLine(s, 1000 + s.seq.misc++ + i, l.variantId, l.quantity, l.unitPrice ?? undefined, l.discount ?? 0, l.taxRate ?? undefined));
    const id = s.seq.invoice++;
    const inv: T.TradeInvoice = {
      id,
      invoiceNumber: "DRAFT",
      status: "DRAFT",
      role: "SELLER",
      seller: sellerCard(s),
      buyer: { ...buyer },
      notes: req.notes,
      deliveryNotes: req.deliveryNotes,
      lines,
      ...invoiceTotals(lines),
      currencyCode: "INR",
      createdAt: now(),
      returns: [],
      events: [{ action: "CREATED", businessId: s.business.id, businessName: s.business.name, actorName: ROLE_ACTOR[me.role].name, at: now() }],
    };
    (inv as T.TradeInvoice & { _variants?: number[] })._variants = req.lines.map((l) => l.variantId);
    s.invoices.unshift(inv);
    if (req.send) sendInvoice(s, inv, me);
    return inv;
  }],
  ["POST", /^\/trade\/invoices\/(\d+)\/send$/, ({ s, m, me }) => {
    forbid(me, "SALE_RECORD");
    const inv = s.invoices.find((i) => i.id === Number(m[1])) ?? notFound("Invoice");
    sendInvoice(s, inv, me);
    return inv;
  }],
  ["DELETE", /^\/trade\/invoices\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "SALE_RECORD");
    const inv = s.invoices.find((i) => i.id === Number(m[1])) ?? notFound("Invoice");
    if (inv.status !== "DRAFT") throw new ApiError({ status: 409, code: "CONFLICT", message: "Only drafts can be deleted" });
    s.invoices = s.invoices.filter((i) => i !== inv);
    return undefined;
  }],
  ["POST", /^\/trade\/invoices\/(\d+)\/(cancel|reject)$/, ({ s, m, me, body }) => {
    const inv = s.invoices.find((i) => i.id === Number(m[1])) ?? notFound("Invoice");
    required({ reason: body.reason });
    if (m[2] === "cancel") {
      forbid(me, "SALE_RECORD");
      if (inv.role !== "SELLER" || !["DRAFT", "PENDING_APPROVAL"].includes(inv.status)) {
        throw new ApiError({ status: 409, code: "CONFLICT", message: inv.status === "POSTED" ? "A posted invoice can't be cancelled; record a return instead." : "This invoice can't be cancelled" });
      }
      releaseReservation(s, inv, me);
      inv.status = "CANCELLED";
      inv.cancelReason = body.reason;
    } else {
      forbid(me, "PURCHASE_MANAGE");
      if (inv.role !== "BUYER" || inv.status !== "PENDING_APPROVAL") throw new ApiError({ status: 409, code: "CONFLICT", message: "This invoice can't be rejected" });
      inv.status = "REJECTED";
      inv.rejectionReason = body.reason;
    }
    inv.respondedAt = now();
    inv.events.push({ action: m[2] === "cancel" ? "CANCELLED" : "REJECTED", businessId: s.business.id, businessName: s.business.name, actorName: ROLE_ACTOR[me.role].name, note: body.reason, at: now() });
    return inv;
  }],
  ["GET", /^\/trade\/invoices\/(\d+)\/preview$/, ({ s, m, me, q }) => {
    forbid(me, "PURCHASE_MANAGE");
    const inv = s.invoices.find((i) => i.id === Number(m[1])) ?? notFound("Invoice");
    const wh = s.warehouses.find((w) => w.id === Number(q.warehouseId)) ?? s.warehouses[0];
    return {
      invoiceId: inv.id,
      warehouseId: wh.id,
      warehouseName: wh.name,
      lines: inv.lines.map((l) => {
        const matches = s.products.filter((p) => !p.archived && p.name === l.productName && unitOf(s, p).code === l.unitCode);
        const cand = matches.map((p) => ({ variantId: p.variants[0].id, productId: p.id, productName: p.name, variantName: p.variants[0].name, unitCode: unitOf(s, p).code, available: available(s, p.variants[0].id, wh.id) }));
        return { lineId: l.id, mode: cand.length === 1 ? "MATCHED" : cand.length > 1 ? "REVIEW" : "NEW", target: cand.length === 1 ? cand[0] : undefined, candidates: cand };
      }),
    } satisfies T.ReceivePreview;
  }],
  ["POST", /^\/trade\/invoices\/(\d+)\/approve$/, ({ s, m, me, body }) => {
    forbid(me, "PURCHASE_MANAGE");
    const inv = s.invoices.find((i) => i.id === Number(m[1])) ?? notFound("Invoice");
    if (inv.status === "POSTED") return inv; // idempotent
    if (inv.role !== "BUYER" || inv.status !== "PENDING_APPROVAL") throw new ApiError({ status: 409, code: "CONFLICT", message: "This invoice can't be approved" });
    const wh = Number(body?.warehouseId) || 1;
    const items: T.PurchaseLineRequest[] = [];
    for (const l of inv.lines) {
      const choice = (body?.lines ?? []).find((c: { lineId: number }) => c.lineId === l.id);
      let variantId: number | undefined = choice?.variantId;
      if (!variantId && !choice?.createNew) {
        const matches = s.products.filter((p) => !p.archived && p.name === l.productName && unitOf(s, p).code === l.unitCode);
        if (matches.length > 1) throw new ApiError({ status: 409, code: "NEEDS_REVIEW", message: "Choose which product receives each highlighted line", fieldErrors: { [`lines[${l.lineNo - 1}]`]: "Several products match" } });
        variantId = matches[0]?.variants[0].id;
      }
      if (!variantId) {
        const id = s.seq.product++;
        const unit = s.units.find((u) => u.code === l.unitCode) ?? s.units[0];
        s.products.push({ id, name: l.productName, unitId: unit.id, salePrice: l.unitPrice, taxRate: l.taxRate, archived: false, createdAt: now(), updatedAt: now(), variants: [{ id, productId: id, name: l.productName, isDefault: true, attributes: {}, archived: false }] });
        variantId = id;
      }
      items.push({ variantId, warehouseId: wh, quantity: l.quantity, unitCost: l.unitPrice, discount: l.discount, taxRate: l.taxRate });
      rememberMapping(s, inv.seller, l.productName, variantId);
    }
    const purchase = recordPurchase(s, { items, supplierName: inv.seller.name, saveSupplier: !s.suppliers.some((x) => x.name === inv.seller.name), referenceNumber: inv.invoiceNumber, clientRequestId: `approve-${inv.id}` }, { at: now(), actor: ROLE_ACTOR[me.role] });
    inv.status = "POSTED";
    inv.purchaseId = purchase.id;
    inv.respondedAt = now();
    inv.warehouseName = s.warehouses.find((w) => w.id === wh)?.name;
    inv.events.push({ action: "APPROVED", businessId: s.business.id, businessName: s.business.name, actorName: ROLE_ACTOR[me.role].name, note: `Stock received into ${inv.warehouseName}`, at: now() });
    return inv;
  }],

  ["GET", /^\/permissions$/, () => ({ permissions: [], roleDefaults: ROLE_DEFAULTS })],
  ["GET", /^\/employees$/, ({ s, q, me }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    const rows = s.employees.filter((e) => contains([e.fullName, e.email], q.q)).map((e) => ({ ...e, permissions: ROLE_DEFAULTS[e.role] }));
    return page(rows, q, 50);
  }],
  ["PUT", /^\/employees\/(\d+)\/role$/, ({ s, m, me, body }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    const e = s.employees.find((x) => x.membershipId === Number(m[1])) ?? notFound("Employee");
    e.role = body.role;
    return { ...e, permissions: ROLE_DEFAULTS[e.role] };
  }],
  ["POST", /^\/employees\/(\d+)\/(deactivate|reactivate)$/, ({ s, m, me }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    const e = s.employees.find((x) => x.membershipId === Number(m[1])) ?? notFound("Employee");
    if (e.membershipId === me.membershipId) throw new ApiError({ status: 400, code: "BAD_REQUEST", message: "You can't deactivate yourself" });
    e.status = m[2] === "deactivate" ? "DEACTIVATED" : "ACTIVE";
    return { ...e, permissions: ROLE_DEFAULTS[e.role] };
  }],
  ["GET", /^\/invitations$/, ({ s, q, me }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    return page(s.invitations.filter((i) => i.status === "PENDING"), q, 50);
  }],
  ["POST", /^\/invitations$/, ({ s, me, body }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    required({ email: body.email, role: body.role });
    const inv: T.Invitation = { id: s.seq.misc++, email: body.email, role: body.role, code: `DEMO${Math.floor(1000 + Math.random() * 9000)}`, status: "PENDING", invitedBy: me.fullName, expiresAt: new Date(Date.now() + 7 * 86400000).toISOString(), createdAt: now() };
    s.invitations.unshift(inv);
    return inv;
  }],
  ["DELETE", /^\/invitations\/(\d+)$/, ({ s, m, me }) => {
    forbid(me, "EMPLOYEE_MANAGE");
    const inv = s.invitations.find((i) => i.id === Number(m[1])) ?? notFound("Invitation");
    inv.status = "CANCELLED";
    return undefined;
  }],

  ["GET", /^\/notifications$/, ({ s, q }) => page(s.notifications.filter((n) => String(q.unreadOnly) !== "true" || !n.read), q, 30)],
  ["POST", /^\/notifications\/read-all$/, ({ s }) => {
    s.notifications.forEach((n) => (n.read = true));
    return undefined;
  }],
  ["POST", /^\/notifications\/(\d+)\/read$/, ({ s, m }) => {
    const n = s.notifications.find((x) => x.id === Number(m[1]));
    if (n) n.read = true;
    return undefined;
  }],
];

function sendInvoice(s: DemoState, inv: T.TradeInvoice, me: T.Me) {
  if (inv.status !== "DRAFT") throw new ApiError({ status: 409, code: "CONFLICT", message: "Only drafts can be sent" });
  const variants = (inv as T.TradeInvoice & { _variants?: number[] })._variants ?? [];
  // Sending reserves the seller's stock: on hand is unchanged, but it can't be sold elsewhere.
  inv.lines.forEach((l, i) => {
    const variantId = variants[i] ?? s.products.find((p) => p.name === l.productName)?.variants[0].id;
    if (variantId) applyMovement(s, { type: "RESERVE", variantId, warehouseId: 1, quantity: l.quantity, reason: `Reserved for invoice`, actor: ROLE_ACTOR[me.role], at: now() });
  });
  const year = new Date().getFullYear();
  inv.invoiceNumber = `DW-${year}-${String(inv.id + 10).padStart(5, "0")}`;
  inv.status = "PENDING_APPROVAL";
  inv.sentAt = now();
  inv.invoiceDate = now().slice(0, 10);
  inv.warehouseName = "Main Warehouse";
  inv.events.push({ action: "SENT", businessId: s.business.id, businessName: s.business.name, actorName: ROLE_ACTOR[me.role].name, at: now() });
}

function releaseReservation(s: DemoState, inv: T.TradeInvoice, me: T.Me) {
  if (inv.status !== "PENDING_APPROVAL") return;
  const variants = (inv as T.TradeInvoice & { _variants?: number[] })._variants ?? [];
  inv.lines.forEach((l, i) => {
    const variantId = variants[i] ?? s.products.find((p) => p.name === l.productName)?.variants[0].id;
    if (!variantId) return;
    const reserved = s.balances.get(balKey(variantId, 1))?.reserved ?? 0;
    const qty = Math.min(reserved, l.quantity);
    if (qty > 0) applyMovement(s, { type: "RELEASE", variantId, warehouseId: 1, quantity: qty, reason: `Released: invoice ${inv.invoiceNumber} cancelled`, actor: ROLE_ACTOR[me.role], at: now() });
  });
}

/** Creates the demo client. `getRole` is read on every request so the role switcher applies immediately. */
export function createDemoClient(getRole: () => T.Role): ApiClient {
  return {
    mode: "demo",
    async request<R>(method: string, path: string, options: RequestOptions = {}): Promise<R> {
      await new Promise((r) => setTimeout(r, 180 + Math.random() * 220)); // let loading states show
      const s = db();
      const role = getRole();
      const me = { ...demoMe(role), permissions: effectivePermissions(s, ROLE_ACTOR[role].id, role) };
      const ctx = { q: options.query ?? {}, body: options.body ?? {}, me, s, actor: ROLE_ACTOR[role] };
      for (const [verb, re, handler] of [...parityRoutes, ...routes] as [string, RegExp, Handler][]) {
        if (verb !== method) continue;
        const m = path.match(re);
        if (!m) continue;
        const result = handler({ ...ctx, m });
        return (result === undefined ? undefined : structuredClone(result)) as R;
      }
      throw new ApiError({ status: 404, code: "NOT_FOUND", message: `Not available in demo mode (${method} ${path})` });
    },
    exportUrl() {
      return null;
    },
    async upload<R>(): Promise<R> {
      throw new ApiError({ status: 400, code: "DEMO_ONLY", message: "File uploads aren't available in demo mode." });
    },
  };
}

