/**
 * DEMO DATA ONLY. A fictional wholesale business used to preview the dashboard without a
 * backend. Nothing here is real business data.
 *
 * The store simulates history chronologically using the same rules the backend applies
 * (docs/API.md): sales decrement stock and snapshot cost, purchases increment stock and update
 * the weighted-average cost, stock never goes negative, and line totals are
 * quantity × price − discount + tax. That keeps every number on every demo screen consistent.
 */
import { ApiError } from "@/lib/api/client";
import type * as T from "@/lib/api/types";

// ------------------------------------------------------------------ helpers

function mulberry32(seed: number) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export const round2 = (n: number) => Math.round(n * 100) / 100;
const round4 = (n: number) => Math.round(n * 10000) / 10000;

function daysAgo(days: number, hour = 11, minute = 0): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

// ------------------------------------------------------------------ store types

export interface DemoVariant {
  id: number;
  productId: number;
  name: string;
  sku?: string;
  barcode?: string;
  isDefault: boolean;
  minStock?: number;
  attributes: Record<string, unknown>;
  archived: boolean;
}

export interface DemoProduct {
  id: number;
  name: string;
  brand?: string;
  categoryId?: number;
  unitId: number;
  description?: string;
  salePrice?: number;
  purchasePrice?: number;
  averageCost?: number;
  taxRate: number;
  minStock?: number;
  supplierId?: number;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
  variants: DemoVariant[];
}

interface Bal {
  onHand: number;
  reserved: number;
  damaged: number;
}

export interface DemoState {
  business: T.Business;
  settings: T.BusinessSettings;
  units: T.Unit[];
  categories: { id: number; name: string; parentId?: number; description?: string; archived: boolean }[];
  attributes: T.Attribute[];
  warehouses: Omit<T.Warehouse, "productCount" | "totalQuantity">[];
  products: DemoProduct[];
  balances: Map<string, Bal>;
  movements: T.Movement[];
  customers: T.Party[];
  suppliers: T.Party[];
  sales: T.Sale[];
  purchases: T.Purchase[];
  employees: T.Employee[];
  invitations: T.Invitation[];
  connections: T.Connection[];
  invoices: T.TradeInvoice[];
  notifications: T.Notification[];
  seq: { movement: number; sale: number; purchase: number; product: number; party: number; misc: number; invoice: number };
}

// ------------------------------------------------------------------ seed definitions

const UNITS: T.Unit[] = [
  { id: 1, code: "pcs", name: "Pieces", decimals: 0, archived: false },
  { id: 2, code: "box", name: "Boxes", decimals: 0, archived: false },
  { id: 3, code: "m", name: "Metres", decimals: 2, archived: false },
  { id: 4, code: "l", name: "Litres", decimals: 3, archived: false },
  { id: 5, code: "kg", name: "Kilograms", decimals: 3, archived: false },
  { id: 6, code: "set", name: "Sets", decimals: 0, archived: false },
  { id: 7, code: "roll", name: "Rolls", decimals: 0, archived: false },
];

const CATEGORIES = [
  { id: 1, name: "Electrical", archived: false },
  { id: 2, name: "Cables & wires", parentId: 1, archived: false },
  { id: 3, name: "Switches & sockets", parentId: 1, archived: false },
  { id: 4, name: "Lighting", parentId: 1, archived: false },
  { id: 5, name: "Hardware", archived: false },
  { id: 6, name: "Fasteners", parentId: 5, archived: false },
  { id: 7, name: "Hand tools", parentId: 5, archived: false },
  { id: 8, name: "Paints", archived: false },
  { id: 9, name: "Plumbing", archived: false },
];

// [name, brand, categoryId, unitId, sku, salePrice, purchasePrice, minStock, opening main, opening branch, supplierId]
type Def = [string, string, number, number, string, number, number, number, number, number, number];
const PRODUCTS: Def[] = [
  ["Copper wire 1.5 sq mm (90 m)", "Voltline", 2, 7, "CW-15-90", 1450, 1180, 15, 70, 20, 1],
  ["Copper wire 2.5 sq mm (90 m)", "Voltline", 2, 7, "CW-25-90", 2280, 1890, 12, 55, 10, 1],
  ["LAN cable Cat6 (305 m)", "NetCore", 2, 7, "LAN-C6", 6400, 5300, 4, 12, 0, 1],
  ["Modular switch 6A", "Brightline", 3, 1, "SW-6A", 48, 31, 100, 900, 200, 2],
  ["Socket 16A with shutter", "Brightline", 3, 1, "SK-16A", 115, 79, 60, 420, 80, 2],
  ["LED bulb 9W (cool white)", "Lumio", 4, 1, "LED-9W", 95, 62, 120, 1100, 300, 2],
  ["LED panel 18W square", "Lumio", 4, 1, "LP-18W", 640, 455, 25, 110, 30, 2],
  ["Self-tapping screws 25 mm (box of 500)", "FixPro", 6, 2, "ST-25-500", 380, 255, 20, 140, 0, 3],
  ["Anchor bolts M10 (box of 50)", "FixPro", 6, 2, "AB-M10-50", 720, 520, 10, 22, 0, 3],
  ["Claw hammer 450 g", "ToolKraft", 7, 1, "HM-450", 410, 290, 10, 45, 10, 3],
  ["Screwdriver set (6 pc)", "ToolKraft", 7, 6, "SD-6PC", 560, 395, 8, 18, 0, 3],
  ["Interior emulsion 20 L (white)", "ColourMax", 8, 4, "EM-20W", 5200, 4300, 6, 26, 0, 4],
  ["Enamel paint 1 L (black)", "ColourMax", 8, 4, "EN-1BK", 340, 245, 20, 80, 20, 4],
  ["PVC pipe 1 in (3 m)", "AquaFlow", 9, 1, "PVC-1-3", 260, 185, 40, 260, 40, 4],
  ["Ball valve 1/2 in brass", "AquaFlow", 9, 1, "BV-12", 330, 236, 25, 30, 0, 4],
  ["Teflon tape 12 mm", "AquaFlow", 9, 7, "TT-12", 18, 9, 200, 0, 0, 4],
];

const CUSTOMER_NAMES = [
  ["Kumar Hardware Store", "+91 90000 10001"],
  ["Lakeview Electricals", "+91 90000 10002"],
  ["Sunrise Builders", "+91 90000 10003"],
  ["Greenfield Interiors", "+91 90000 10004"],
  ["Metro Maintenance Services", "+91 90000 10005"],
  ["Walk-in customer", undefined],
] as const;

const SUPPLIER_NAMES = [
  ["Voltline Cables Pvt (sample)", "27ABCDE1234F1Z5"],
  ["Brightline Electricals (sample)", "27ABCDE1234F1Z6"],
  ["FixPro Industrial (sample)", "27ABCDE1234F1Z7"],
  ["ColourMax & AquaFlow Distributors (sample)", "27ABCDE1234F1Z8"],
] as const;

// ------------------------------------------------------------------ store

const ACTORS = [
  { id: 1, name: "Demo Admin" },
  { id: 2, name: "Asha Rao" },
  { id: 3, name: "Vikram Singh" },
];

export function balKey(variantId: number, warehouseId: number) {
  return `${variantId}:${warehouseId}`;
}

export function createStore(): DemoState {
  const now = new Date().toISOString();
  const s: DemoState = {
    business: {
      id: 900,
      name: "Demo Wholesale Co.",
      businessType: "Electrical & hardware wholesaler",
      email: "accounts@demo.example",
      phone: "+91 90000 00000",
      address: "Plot 12, Sample Industrial Area",
      city: "Pune",
      state: "Maharashtra",
      country: "India",
      postalCode: "411000",
      createdAt: daysAgo(120),
      businessCode: "DEMO7K3F",
      taxId: "27AAAAA0000A1Z5",
    },
    settings: {
      allowNegativeStock: false,
      defaultLowStockThreshold: 5,
      trackBatches: false,
      trackExpiry: false,
      trackSerials: false,
      trackDamagedReserved: true,
      notifyLowStock: true,
      timezone: "Asia/Kolkata",
      currencyCode: "INR",
    },
    units: UNITS,
    categories: CATEGORIES,
    attributes: [
      { id: 1, key: "wattage", label: "Wattage", fieldType: "NUMBER", required: false, categoryId: 4, categoryName: "Lighting", archived: false, sortOrder: 1 },
      { id: 2, key: "colour_temp", label: "Colour temperature", fieldType: "DROPDOWN", required: false, options: ["Warm white", "Neutral white", "Cool white"], categoryId: 4, categoryName: "Lighting", archived: false, sortOrder: 2 },
      { id: 3, key: "core_size", label: "Core size (sq mm)", fieldType: "NUMBER", required: true, categoryId: 2, categoryName: "Cables & wires", archived: false, sortOrder: 1 },
      { id: 4, key: "finish", label: "Finish", fieldType: "DROPDOWN", required: false, options: ["Matt", "Gloss", "Satin"], categoryId: 8, categoryName: "Paints", archived: false, sortOrder: 1 },
    ],
    warehouses: [
      { id: 1, name: "Main Warehouse", code: "MAIN", address: "Plot 12, Sample Industrial Area", branch: false, isDefault: true, archived: false },
      { id: 2, name: "City Branch", code: "CITY", address: "Shop 4, Market Road", branch: true, isDefault: false, archived: false },
    ],
    products: [],
    balances: new Map(),
    movements: [],
    customers: CUSTOMER_NAMES.map(([name, phone], i) => ({
      id: i + 1,
      name,
      phone,
      email: phone ? `orders${i + 1}@customer.example` : undefined,
      archived: false,
      createdAt: daysAgo(110 - i),
    })),
    suppliers: SUPPLIER_NAMES.map(([name, taxId], i) => ({
      id: i + 1,
      name,
      taxId,
      phone: `+91 90000 2000${i + 1}`,
      email: `sales${i + 1}@supplier.example`,
      archived: false,
      createdAt: daysAgo(118 - i),
    })),
    sales: [],
    purchases: [],
    employees: [
      { membershipId: 1, userId: 1, fullName: "Demo Admin", email: "admin@demo.example", role: "ADMIN", status: "ACTIVE", permissions: [], overrides: {}, joinedAt: daysAgo(120) },
      { membershipId: 2, userId: 2, fullName: "Asha Rao", email: "asha@demo.example", role: "INVENTORY_MANAGER", status: "ACTIVE", permissions: [], overrides: {}, joinedAt: daysAgo(100) },
      { membershipId: 3, userId: 3, fullName: "Vikram Singh", email: "vikram@demo.example", role: "STAFF", status: "ACTIVE", permissions: [], overrides: {}, joinedAt: daysAgo(60) },
    ],
    invitations: [
      { id: 1, email: "new.staff@demo.example", role: "STAFF", code: "DEMO1234", status: "PENDING", invitedBy: "Demo Admin", expiresAt: daysAgo(-5), createdAt: daysAgo(2) },
    ],
    connections: [
      { id: 1, status: "ACCEPTED", direction: "OUTGOING", business: { id: 901, name: "Northline Retail (sample)", businessType: "Retailer", city: "Nashik", businessCode: "NRTH21AB" }, createdAt: daysAgo(80), respondedAt: daysAgo(79) },
      { id: 2, status: "ACCEPTED", direction: "INCOMING", business: { id: 902, name: "Riverside Stores (sample)", businessType: "Retailer", city: "Satara", businessCode: "RVSD55CD" }, createdAt: daysAgo(50), respondedAt: daysAgo(50) },
      { id: 3, status: "ACCEPTED", direction: "INCOMING", business: { id: 903, name: "Metro Cable Mills (sample)", businessType: "Manufacturer", city: "Mumbai", businessCode: "MTRO88EF" }, createdAt: daysAgo(40), respondedAt: daysAgo(39) },
    ],
    invoices: [],
    notifications: [],
    seq: { movement: 1, sale: 1, purchase: 1, product: 100, party: 50, misc: 500, invoice: 1 },
  };

  // Products with opening stock 90 days ago.
  PRODUCTS.forEach((def, i) => {
    const [name, brand, categoryId, unitId, sku, sale, cost, min, openMain, openBranch, supplierId] = def;
    const id = i + 1;
    const created = daysAgo(92 - (i % 3));
    s.products.push({
      id,
      name,
      brand,
      categoryId,
      unitId,
      salePrice: sale,
      purchasePrice: cost,
      averageCost: cost,
      taxRate: 18,
      minStock: min,
      supplierId,
      archived: false,
      createdAt: created,
      updatedAt: created,
      variants: [{ id, productId: id, name, sku, barcode: `89000000${String(id).padStart(5, "0")}`, isDefault: true, attributes: {}, archived: false }],
    });
    if (openMain > 0) applyMovement(s, { type: "OPENING", variantId: id, warehouseId: 1, quantity: openMain, reason: "Opening stock", actor: ACTORS[0], at: daysAgo(90, 9) });
    if (openBranch > 0) applyMovement(s, { type: "OPENING", variantId: id, warehouseId: 2, quantity: openBranch, reason: "Opening stock", actor: ACTORS[0], at: daysAgo(90, 9, 30) });
  });

  // 90 days of trading history.
  const rand = mulberry32(20261025);
  for (let day = 88; day >= 0; day--) {
    // Purchases roughly weekly restock the fast movers.
    if (day % 7 === 3) {
      const supplier = s.suppliers[Math.floor(rand() * s.suppliers.length)];
      const candidates = s.products.filter((p) => p.supplierId === supplier.id && p.id !== 16 && p.id !== 9);
      const items = candidates.slice(0, 3).map((p) => ({
        variantId: p.id,
        warehouseId: 1,
        quantity: Math.max(5, Math.round((p.minStock ?? 10) * (1.5 + rand()))),
        unitCost: round2((p.purchasePrice ?? 0) * (0.97 + rand() * 0.08)),
        discount: 0,
        taxRate: 18,
      }));
      if (items.length) {
        recordPurchase(
          s,
          { items, supplierId: supplier.id, referenceNumber: `BILL-${1200 + day}`, payment: rand() > 0.3 ? { method: "BANK_TRANSFER", amount: 0 } : null, clientRequestId: `seed-p-${day}` },
          { at: daysAgo(day, 10), actor: ACTORS[1], payFull: true },
        );
      }
    }

    // 1–4 sales per day, busier recently.
    const salesToday = 1 + Math.floor(rand() * (day < 30 ? 4 : 3));
    for (let k = 0; k < salesToday; k++) {
      const customer = s.customers[Math.floor(rand() * s.customers.length)];
      const lineCount = 1 + Math.floor(rand() * 3);
      const used = new Set<number>();
      const items: T.SaleLineRequest[] = [];
      for (let l = 0; l < lineCount; l++) {
        // Weighted towards the first products (fast movers); products 11 and 15 rarely sell.
        let p = s.products[Math.floor(Math.pow(rand(), 1.6) * s.products.length)];
        if ((p.id === 11 || p.id === 15) && rand() > 0.15) p = s.products[3];
        if (used.has(p.id)) continue;
        used.add(p.id);
        const unit = UNITS.find((u) => u.id === p.unitId)!;
        const base = unit.code === "pcs" ? 5 + Math.floor(rand() * 40) : 1 + Math.floor(rand() * 6);
        const warehouseId = rand() > 0.8 ? 2 : 1;
        if (available(s, p.id, warehouseId) < base) continue;
        items.push({ variantId: p.id, warehouseId, quantity: base, unitPrice: p.salePrice ?? 0, discount: rand() > 0.85 ? round2((p.salePrice ?? 0) * base * 0.03) : 0, taxRate: 18 });
      }
      if (!items.length) continue;
      const pay = rand();
      recordSale(
        s,
        { items, customerId: customer.id, clientRequestId: `seed-s-${day}-${k}`, payment: null },
        { at: daysAgo(day, 10 + k * 2, Math.floor(rand() * 59)), actor: ACTORS[1 + Math.floor(rand() * 2)], payRatio: day > 20 ? (pay > 0.15 ? 1 : 0.5) : pay > 0.4 ? 1 : pay > 0.15 ? 0.5 : 0 },
      );
    }

    // Occasional manual stock corrections.
    if (day === 40) applyMovement(s, { type: "ADJUST", variantId: 6, warehouseId: 1, quantity: -2, reason: "Counting error", actor: ACTORS[1], at: daysAgo(day, 17) });
    if (day === 12 && available(s, 13, 1) >= 3) applyMovement(s, { type: "DAMAGE", variantId: 13, warehouseId: 1, quantity: 3, reason: "Tins dented in transit", actor: ACTORS[1], at: daysAgo(day, 16) });
    if (day === 5 && available(s, 5, 1) >= 40) transfer(s, { variantId: 5, fromWarehouseId: 1, toWarehouseId: 2, quantity: 40, reason: "Branch restock" }, ACTORS[1], daysAgo(day, 12));
  }

  // B2B invoices (sample counterparties).
  seedInvoices(s);
  s.movements.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  s.sales.sort((a, b) => b.soldAt.localeCompare(a.soldAt));
  seedNotifications(s);
  s.business.createdAt = s.business.createdAt ?? now;
  return s;
}

// ------------------------------------------------------------------ stock engine

export function bal(s: DemoState, variantId: number, warehouseId: number): Bal {
  const key = balKey(variantId, warehouseId);
  let b = s.balances.get(key);
  if (!b) {
    b = { onHand: 0, reserved: 0, damaged: 0 };
    s.balances.set(key, b);
  }
  return b;
}

export function available(s: DemoState, variantId: number, warehouseId?: number): number {
  if (warehouseId !== undefined) {
    const b = bal(s, variantId, warehouseId);
    return b.onHand - b.reserved - b.damaged;
  }
  return s.warehouses.reduce((sum, w) => sum + available(s, variantId, w.id), 0);
}

export function totals(s: DemoState, variantId: number): T.StockTotals {
  const t = { onHand: 0, reserved: 0, damaged: 0, available: 0 };
  for (const w of s.warehouses) {
    const b = bal(s, variantId, w.id);
    t.onHand += b.onHand;
    t.reserved += b.reserved;
    t.damaged += b.damaged;
  }
  t.available = t.onHand - t.reserved - t.damaged;
  return t;
}

export function findVariant(s: DemoState, variantId: number): { product: DemoProduct; variant: DemoVariant } {
  for (const product of s.products) {
    const variant = product.variants.find((v) => v.id === variantId);
    if (variant) return { product, variant };
  }
  throw new ApiError({ status: 404, code: "NOT_FOUND", message: "Product not found" });
}

export function unitOf(s: DemoState, p: DemoProduct): T.Unit {
  return s.units.find((u) => u.id === p.unitId) ?? s.units[0];
}

export function threshold(s: DemoState, p: DemoProduct, v?: DemoVariant): number {
  return v?.minStock ?? p.minStock ?? s.settings.defaultLowStockThreshold;
}

export function statusOf(availableQty: number, min: number): T.StockStatus {
  if (availableQty <= 0) return "OUT_OF_STOCK";
  if (availableQty <= min) return "LOW_STOCK";
  return "IN_STOCK";
}

interface MoveInput {
  type: T.MovementType;
  variantId: number;
  warehouseId: number;
  quantity: number;
  reason?: string;
  notes?: string;
  actor: { id: number; name: string };
  at: string;
}

const INCREASE: T.MovementType[] = ["OPENING", "ADD", "TRANSFER_IN", "SALE_CANCEL", "RETURN_IN", "PURCHASE"];
const DECREASE: T.MovementType[] = ["REDUCE", "TRANSFER_OUT", "SALE", "PURCHASE_CANCEL", "PURCHASE_RETURN"];

export function applyMovement(s: DemoState, m: MoveInput, transferGroupId?: string): T.Movement {
  const { product, variant } = findVariant(s, m.variantId);
  const warehouse = s.warehouses.find((w) => w.id === m.warehouseId);
  if (!warehouse) throw new ApiError({ status: 404, code: "NOT_FOUND", message: "Warehouse not found" });
  const b = bal(s, m.variantId, m.warehouseId);
  const avail = b.onHand - b.reserved - b.damaged;
  let signed = 0;
  let bucket = "ON_HAND";
  const before = b.onHand;

  if (INCREASE.includes(m.type)) {
    signed = m.quantity;
    b.onHand += m.quantity;
  } else if (DECREASE.includes(m.type)) {
    if (!s.settings.allowNegativeStock && avail < m.quantity) {
      throw new ApiError({
        status: 422,
        code: "INSUFFICIENT_STOCK",
        message: `Not enough stock for ${product.name} in ${warehouse.name}: ${avail} available, ${m.quantity} requested.`,
      });
    }
    signed = -m.quantity;
    b.onHand -= m.quantity;
  } else if (m.type === "ADJUST" || m.type === "COUNT_ADJUST") {
    // Callers pass the delta; the request API takes the corrected on-hand value (see client.ts).
    signed = m.quantity;
    if (!s.settings.allowNegativeStock && avail + signed < 0) {
      throw new ApiError({ status: 422, code: "INSUFFICIENT_STOCK", message: "Adjustment would make available stock negative." });
    }
    b.onHand += signed;
  } else if (m.type === "DAMAGE" || m.type === "RESERVE") {
    if (avail < m.quantity) throw new ApiError({ status: 422, code: "INSUFFICIENT_STOCK", message: `Only ${avail} available.` });
    bucket = m.type === "DAMAGE" ? "DAMAGED" : "RESERVED";
    if (m.type === "DAMAGE") b.damaged += m.quantity;
    else b.reserved += m.quantity;
    signed = m.quantity;
  } else if (m.type === "RELEASE") {
    if (b.reserved < m.quantity) throw new ApiError({ status: 422, code: "INSUFFICIENT_STOCK", message: `Only ${b.reserved} reserved.` });
    b.reserved -= m.quantity;
    bucket = "RESERVED";
    signed = -m.quantity;
  }

  const mv: T.Movement = {
    id: s.seq.movement++,
    type: m.type,
    bucket,
    productId: product.id,
    productName: product.name,
    variantId: variant.id,
    variantName: variant.name,
    sku: variant.sku,
    warehouseId: warehouse.id,
    warehouseName: warehouse.name,
    quantity: signed,
    quantityBefore: bucket === "ON_HAND" ? before : 0,
    quantityAfter: bucket === "ON_HAND" ? b.onHand : 0,
    unitCode: unitOf(s, product).code,
    reason: m.reason,
    notes: m.notes,
    actorId: m.actor.id,
    actorName: m.actor.name,
    transferGroupId,
    createdAt: m.at,
  };
  s.movements.unshift(mv);
  product.updatedAt = m.at;
  return mv;
}

export function transfer(
  s: DemoState,
  req: Pick<T.TransferRequest, "variantId" | "fromWarehouseId" | "toWarehouseId" | "quantity" | "reason" | "notes">,
  actor: { id: number; name: string },
  at: string,
): T.Movement[] {
  if (req.fromWarehouseId === req.toWarehouseId) {
    throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Choose two different warehouses", fieldErrors: { toWarehouseId: "Choose a different warehouse" } });
  }
  const group = `demo-${s.seq.misc++}`;
  const out = applyMovement(s, { type: "TRANSFER_OUT", variantId: req.variantId, warehouseId: req.fromWarehouseId, quantity: req.quantity, reason: req.reason ?? "Transfer", notes: req.notes, actor, at }, group);
  const inn = applyMovement(s, { type: "TRANSFER_IN", variantId: req.variantId, warehouseId: req.toWarehouseId, quantity: req.quantity, reason: req.reason ?? "Transfer", notes: req.notes, actor, at }, group);
  return [out, inn];
}

// ------------------------------------------------------------------ sales & purchases

function lineAmounts(quantity: number, price: number, discount: number, taxRate: number) {
  const gross = round2(quantity * price);
  if (discount > gross) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Discount can't exceed the line amount" });
  const net = round2(gross - discount);
  const tax = round2((net * taxRate) / 100);
  return { gross, net, tax, total: round2(net + tax) };
}

function paymentStatus(total: number, returned: number, paid: number): T.PaymentStatus {
  const due = total - returned;
  if (due <= 0 || paid >= due) return "PAID";
  if (paid <= 0) return "UNPAID";
  return "PARTIALLY_PAID";
}

export function recordSale(
  s: DemoState,
  req: T.SaleRequest,
  ctx: { at: string; actor: { id: number; name: string }; payRatio?: number },
): T.Sale {
  if (!req.items.length) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Add at least one product" });
  // Check every line first: a sale is all-or-nothing.
  const need = new Map<string, number>();
  for (const it of req.items) {
    const wh = it.warehouseId ?? 1;
    const key = balKey(it.variantId, wh);
    need.set(key, (need.get(key) ?? 0) + it.quantity);
  }
  for (const [key, q] of need) {
    const [variantId, wh] = key.split(":").map(Number);
    if (!s.settings.allowNegativeStock && available(s, variantId, wh) < q) {
      const { product } = findVariant(s, variantId);
      throw new ApiError({ status: 422, code: "INSUFFICIENT_STOCK", message: `Not enough stock for ${product.name}: ${available(s, variantId, wh)} available, ${q} requested.` });
    }
  }

  const id = s.seq.sale++;
  const invoiceNumber = req.invoiceNumber || `INV-${String(id).padStart(5, "0")}`;
  const items: T.SaleItem[] = req.items.map((it, i) => {
    const { product, variant } = findVariant(s, it.variantId);
    const taxRate = it.taxRate ?? product.taxRate;
    const a = lineAmounts(it.quantity, it.unitPrice, it.discount ?? 0, taxRate);
    const unitCost = product.averageCost ?? product.purchasePrice;
    const wh = it.warehouseId ?? 1;
    applyMovement(s, { type: "SALE", variantId: variant.id, warehouseId: wh, quantity: it.quantity, reason: `Sale ${invoiceNumber}`, actor: ctx.actor, at: ctx.at });
    return {
      id: id * 100 + i,
      lineNo: i + 1,
      productId: product.id,
      productName: product.name,
      variantId: variant.id,
      variantName: variant.name,
      sku: variant.sku,
      unitCode: unitOf(s, product).code,
      warehouseName: s.warehouses.find((w) => w.id === wh)?.name,
      quantity: it.quantity,
      returnedQuantity: 0,
      unitPrice: it.unitPrice,
      grossAmount: a.gross,
      discount: it.discount ?? 0,
      netAmount: a.net,
      taxRate,
      taxAmount: a.tax,
      lineTotal: a.total,
      unitCost: unitCost !== undefined ? round4(unitCost) : undefined,
      costAmount: unitCost !== undefined ? round2(unitCost * it.quantity) : undefined,
      grossProfit: unitCost !== undefined ? round2(a.net - unitCost * it.quantity) : undefined,
    };
  });

  const subtotal = round2(items.reduce((x, i) => x + i.grossAmount, 0));
  const discountTotal = round2(items.reduce((x, i) => x + i.discount, 0));
  const taxTotal = round2(items.reduce((x, i) => x + i.taxAmount, 0));
  const total = round2(items.reduce((x, i) => x + i.lineTotal, 0));
  const netSales = round2(subtotal - discountTotal);
  const costKnown = items.every((i) => i.costAmount !== undefined);
  const costTotal = costKnown ? round2(items.reduce((x, i) => x + (i.costAmount ?? 0), 0)) : undefined;

  let paidAmount = req.payment?.amount ?? 0;
  if (ctx.payRatio !== undefined) paidAmount = round2(total * ctx.payRatio);
  if (paidAmount > total) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Payment can't exceed the sale total", fieldErrors: { "payment.amount": "Payment can't exceed the total" } });

  let customerName = req.customerName;
  let customerId = req.customerId ?? undefined;
  if (customerId) customerName = s.customers.find((c) => c.id === customerId)?.name;
  else if (customerName && req.saveCustomer) {
    const c: T.Party = { id: s.seq.party++, name: customerName, phone: req.customerPhone, archived: false, createdAt: ctx.at };
    s.customers.push(c);
    customerId = c.id;
  }

  const sale: T.Sale = {
    id,
    invoiceNumber,
    status: "COMPLETED",
    soldAt: ctx.at,
    customerId,
    customerName,
    customerPhone: req.customerPhone,
    notes: req.notes,
    soldBy: ctx.actor.name,
    subtotal,
    discountTotal,
    taxTotal,
    total,
    returnedTotal: 0,
    amountPaid: paidAmount,
    outstanding: round2(total - paidAmount),
    paymentStatus: paymentStatus(total, 0, paidAmount),
    netSales,
    costTotal,
    grossProfit: costTotal !== undefined ? round2(netSales - costTotal) : undefined,
    items,
    payments:
      paidAmount > 0
        ? [{ id: s.seq.misc++, kind: "SALE_PAYMENT", amount: paidAmount, method: req.payment?.method ?? "CASH", reference: req.payment?.reference, paidAt: ctx.at, recordedBy: ctx.actor.name }]
        : [],
    returns: [],
  };
  s.sales.unshift(sale);
  return sale;
}

export function recordPurchase(
  s: DemoState,
  req: T.PurchaseRequest,
  ctx: { at: string; actor: { id: number; name: string }; payFull?: boolean },
): T.Purchase {
  if (!req.items.length) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Add at least one product" });
  const id = s.seq.purchase++;
  const purchaseNumber = `PUR-${String(id).padStart(5, "0")}`;
  const items: T.PurchaseItem[] = req.items.map((it, i) => {
    const { product, variant } = findVariant(s, it.variantId);
    const taxRate = it.taxRate ?? 0;
    const a = lineAmounts(it.quantity, it.unitCost, it.discount ?? 0, taxRate);
    const landed = round4(a.net / it.quantity);
    // Weighted-average cost: (stock before × old average + received × landed) ÷ stock after.
    const before = totals(s, variant.id).onHand;
    const oldAvg = product.averageCost;
    product.averageCost = before > 0 && oldAvg !== undefined ? round4((before * oldAvg + it.quantity * landed) / (before + it.quantity)) : landed;
    product.purchasePrice = round2(landed);
    const wh = it.warehouseId ?? 1;
    applyMovement(s, { type: "PURCHASE", variantId: variant.id, warehouseId: wh, quantity: it.quantity, reason: `Purchase ${purchaseNumber}`, actor: ctx.actor, at: ctx.at });
    return {
      id: id * 100 + i,
      lineNo: i + 1,
      productId: product.id,
      productName: product.name,
      variantId: variant.id,
      variantName: variant.name,
      unitCode: unitOf(s, product).code,
      warehouseName: s.warehouses.find((w) => w.id === wh)?.name,
      quantity: it.quantity,
      unitCost: it.unitCost,
      grossAmount: a.gross,
      discount: it.discount ?? 0,
      netAmount: a.net,
      taxRate,
      taxAmount: a.tax,
      lineTotal: a.total,
      landedUnitCost: landed,
    };
  });
  const total = round2(items.reduce((x, i) => x + i.lineTotal, 0));
  let paid = req.payment?.amount ?? 0;
  if (ctx.payFull && req.payment) paid = total;
  if (paid > total) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: "Payment can't exceed the purchase total", fieldErrors: { "payment.amount": "Payment can't exceed the total" } });

  let supplierId = req.supplierId ?? undefined;
  let supplierName = req.supplierName;
  if (supplierId) supplierName = s.suppliers.find((x) => x.id === supplierId)?.name;
  else if (supplierName && req.saveSupplier) {
    const sup: T.Party = { id: s.seq.party++, name: supplierName, archived: false, createdAt: ctx.at };
    s.suppliers.push(sup);
    supplierId = sup.id;
  }

  const purchase: T.Purchase = {
    id,
    purchaseNumber,
    referenceNumber: req.referenceNumber,
    status: "RECEIVED",
    purchasedAt: ctx.at,
    supplierId,
    supplierName,
    subtotal: round2(items.reduce((x, i) => x + i.grossAmount, 0)),
    discountTotal: round2(items.reduce((x, i) => x + i.discount, 0)),
    taxTotal: round2(items.reduce((x, i) => x + i.taxAmount, 0)),
    total,
    amountPaid: paid,
    outstanding: round2(total - paid),
    paymentStatus: paymentStatus(total, 0, paid),
    notes: req.notes,
    recordedBy: ctx.actor.name,
    items,
    payments: paid > 0 ? [{ id: s.seq.misc++, kind: "PURCHASE_PAYMENT", amount: paid, method: req.payment?.method ?? "CASH", paidAt: ctx.at, recordedBy: ctx.actor.name }] : [],
  };
  s.purchases.unshift(purchase);
  return purchase;
}

export function addSalePayment(s: DemoState, sale: T.Sale, amount: number, method: string, reference: string | undefined, actor: string) {
  if (sale.status === "CANCELLED") throw new ApiError({ status: 409, code: "CONFLICT", message: "This sale is cancelled" });
  if (amount > sale.outstanding + 0.001) throw new ApiError({ status: 400, code: "VALIDATION_FAILED", message: `Amount exceeds the outstanding ${sale.outstanding}`, fieldErrors: { amount: "Amount exceeds the outstanding balance" } });
  sale.payments.push({ id: s.seq.misc++, kind: "SALE_PAYMENT", amount, method, reference, paidAt: new Date().toISOString(), recordedBy: actor });
  sale.amountPaid = round2(sale.amountPaid + amount);
  sale.outstanding = round2(sale.total - sale.returnedTotal - sale.amountPaid);
  sale.paymentStatus = paymentStatus(sale.total, sale.returnedTotal, sale.amountPaid);
}

export function cancelSale(s: DemoState, sale: T.Sale, reason: string, refundMethod: string, actor: { id: number; name: string }) {
  if (sale.status === "CANCELLED") throw new ApiError({ status: 409, code: "CONFLICT", message: "This sale is already cancelled" });
  const at = new Date().toISOString();
  for (const it of sale.items) {
    const q = it.quantity - it.returnedQuantity;
    const wh = s.warehouses.find((w) => w.name === it.warehouseName)?.id ?? 1;
    if (q > 0) applyMovement(s, { type: "SALE_CANCEL", variantId: it.variantId, warehouseId: wh, quantity: q, reason: `Cancelled ${sale.invoiceNumber}`, actor, at });
  }
  if (sale.amountPaid > 0) {
    sale.payments.push({ id: s.seq.misc++, kind: "REFUND", amount: sale.amountPaid, method: refundMethod, paidAt: at, recordedBy: actor.name });
  }
  sale.amountPaid = 0;
  sale.outstanding = 0;
  sale.status = "CANCELLED";
  sale.cancelReason = reason;
  sale.cancelledAt = at;
  sale.cancelledBy = actor.name;
}

export function cancelPurchase(s: DemoState, p: T.Purchase, reason: string, actor: { id: number; name: string }) {
  if (p.status === "CANCELLED") throw new ApiError({ status: 409, code: "CONFLICT", message: "This purchase is already cancelled" });
  const at = new Date().toISOString();
  for (const it of p.items) {
    const wh = s.warehouses.find((w) => w.name === it.warehouseName)?.id ?? 1;
    if (available(s, it.variantId, wh) < it.quantity) {
      throw new ApiError({ status: 422, code: "INSUFFICIENT_STOCK", message: `Some of ${it.productName} from this purchase has already been sold, so it can't be cancelled.` });
    }
  }
  for (const it of p.items) {
    const wh = s.warehouses.find((w) => w.name === it.warehouseName)?.id ?? 1;
    applyMovement(s, { type: "PURCHASE_CANCEL", variantId: it.variantId, warehouseId: wh, quantity: it.quantity, reason: `Cancelled ${p.purchaseNumber}`, actor, at });
  }
  p.status = "CANCELLED";
  p.cancelReason = reason;
  p.cancelledAt = at;
  p.outstanding = 0;
}

// ------------------------------------------------------------------ invoices & notifications

function party(card: T.BusinessCard): T.PartyDetails {
  return { ...card, phone: "+91 90000 30000" };
}

export function sellerCard(s: DemoState): T.PartyDetails {
  return { id: s.business.id, name: s.business.name, businessType: s.business.businessType, city: s.business.city, businessCode: s.business.businessCode, phone: s.business.phone, taxId: s.business.taxId };
}

export function invoiceLine(s: DemoState, lineId: number, variantId: number, quantity: number, unitPrice?: number, discount = 0, taxRate?: number): T.TradeLine {
  const { product, variant } = findVariant(s, variantId);
  const unit = unitOf(s, product);
  const price = unitPrice ?? product.salePrice ?? 0;
  const rate = taxRate ?? product.taxRate;
  const a = lineAmounts(quantity, price, discount, rate);
  return {
    id: lineId,
    lineNo: 1,
    productName: product.name,
    variantName: variant.name,
    defaultVariant: variant.isDefault,
    sku: variant.sku,
    unitCode: unit.code,
    unitName: unit.name,
    unitDecimals: unit.decimals,
    quantity,
    unitPrice: price,
    grossAmount: a.gross,
    discount,
    netAmount: a.net,
    taxRate: rate,
    taxAmount: a.tax,
    lineTotal: a.total,
    returnedQuantity: 0,
  };
}

export function invoiceTotals(lines: T.TradeLine[]) {
  lines.forEach((l, i) => (l.lineNo = i + 1));
  return {
    subtotal: round2(lines.reduce((x, l) => x + l.grossAmount, 0)),
    discountTotal: round2(lines.reduce((x, l) => x + l.discount, 0)),
    taxTotal: round2(lines.reduce((x, l) => x + l.taxAmount, 0)),
    total: round2(lines.reduce((x, l) => x + l.lineTotal, 0)),
  };
}

function seedInvoices(s: DemoState) {
  const me = sellerCard(s);
  const [northline, riverside, metro] = s.connections.map((c) => c.business);
  // Make sure the seeded invoices have stock to reserve or sell, whatever the simulated history did.
  const ensure = (variantId: number, want: number, at: string) => {
    const have = available(s, variantId, 1);
    if (have < want) applyMovement(s, { type: "ADD", variantId, warehouseId: 1, quantity: want - have + 10, reason: "New stock received", actor: ACTORS[1], at });
  };
  ensure(6, 200, daysAgo(10, 9));
  ensure(4, 150, daysAgo(10, 9));
  ensure(1, 10, daysAgo(2, 9));
  ensure(7, 12, daysAgo(2, 9));

  // 1. Posted sales invoice to a retailer (seller side): recorded as an ordinary sale.
  const postedLines = [invoiceLine(s, 1, 6, 200), invoiceLine(s, 2, 4, 150)];
  const postedSale = recordSale(
    s,
    { items: [{ variantId: 6, quantity: 200, unitPrice: 95, taxRate: 18 }, { variantId: 4, quantity: 150, unitPrice: 48, taxRate: 18 }], customerName: northline.name, saveCustomer: true, invoiceNumber: "DW-2026-00001", clientRequestId: "seed-inv-1" },
    { at: daysAgo(9, 15), actor: ACTORS[0], payRatio: 0 },
  );
  s.invoices.push({
    id: s.seq.invoice++, invoiceNumber: "DW-2026-00001", status: "POSTED", role: "SELLER", seller: me, buyer: party(northline),
    invoiceDate: daysAgo(10).slice(0, 10), notes: "Deliver to rear gate", warehouseName: "Main Warehouse", lines: postedLines, ...invoiceTotals(postedLines),
    currencyCode: "INR", createdAt: daysAgo(10, 11), sentAt: daysAgo(10, 11, 5), respondedAt: daysAgo(9, 15), saleId: postedSale.id, returns: [],
    events: [
      { action: "CREATED", businessId: me.id, businessName: me.name, actorName: "Demo Admin", at: daysAgo(10, 11) },
      { action: "SENT", businessId: me.id, businessName: me.name, actorName: "Demo Admin", at: daysAgo(10, 11, 5) },
      { action: "APPROVED", businessId: northline.id, businessName: northline.name, note: "Stock received into Main Store", at: daysAgo(9, 15) },
    ],
  });

  // 2. Sent and awaiting the buyer: seller stock is reserved.
  const pendingLines = [invoiceLine(s, 3, 1, 10), invoiceLine(s, 4, 7, 12)];
  applyMovement(s, { type: "RESERVE", variantId: 1, warehouseId: 1, quantity: 10, reason: "Reserved for DW-2026-00002", actor: ACTORS[0], at: daysAgo(1, 12) });
  applyMovement(s, { type: "RESERVE", variantId: 7, warehouseId: 1, quantity: 12, reason: "Reserved for DW-2026-00002", actor: ACTORS[0], at: daysAgo(1, 12) });
  s.invoices.push({
    id: s.seq.invoice++, invoiceNumber: "DW-2026-00002", status: "PENDING_APPROVAL", role: "SELLER", seller: me, buyer: party(riverside),
    invoiceDate: daysAgo(1).slice(0, 10), warehouseName: "Main Warehouse", lines: pendingLines, ...invoiceTotals(pendingLines), currencyCode: "INR",
    createdAt: daysAgo(1, 11), sentAt: daysAgo(1, 12), returns: [],
    events: [
      { action: "CREATED", businessId: me.id, businessName: me.name, actorName: "Asha Rao", at: daysAgo(1, 11) },
      { action: "SENT", businessId: me.id, businessName: me.name, actorName: "Asha Rao", at: daysAgo(1, 12) },
    ],
  });

  // 3. Draft (seller only, no stock held).
  const draftLines = [invoiceLine(s, 5, 14, 60)];
  s.invoices.push({
    id: s.seq.invoice++, invoiceNumber: "DRAFT", status: "DRAFT", role: "SELLER", seller: me, buyer: party(northline),
    lines: draftLines, ...invoiceTotals(draftLines), currencyCode: "INR", createdAt: daysAgo(0, 9), returns: [],
    events: [{ action: "CREATED", businessId: me.id, businessName: me.name, actorName: "Demo Admin", at: daysAgo(0, 9) }],
  });

  // 4. Incoming purchase invoice from a manufacturer, waiting for our approval.
  const inLine: T.TradeLine = { ...invoiceLine(s, 6, 2, 20, 1820), productName: "Copper wire 2.5 sq mm (90 m)" };
  const inLine2: T.TradeLine = { ...invoiceLine(s, 7, 3, 4, 5150), productName: "LAN cable Cat6 (305 m)" };
  const inLines = [inLine, inLine2];
  s.invoices.push({
    id: s.seq.invoice++, invoiceNumber: "MC-2026-00418", status: "PENDING_APPROVAL", role: "BUYER", seller: party(metro), buyer: me,
    invoiceDate: daysAgo(0).slice(0, 10), notes: "Dispatch by road, 2 cartons", lines: inLines, ...invoiceTotals(inLines), currencyCode: "INR",
    createdAt: daysAgo(0, 8), sentAt: daysAgo(0, 8, 10), returns: [],
    events: [
      { action: "CREATED", businessId: metro.id, businessName: metro.name, at: daysAgo(0, 8) },
      { action: "SENT", businessId: metro.id, businessName: metro.name, at: daysAgo(0, 8, 10) },
    ],
  });
}

function seedNotifications(s: DemoState) {
  let id = 1;
  for (const p of s.products) {
    const t = totals(s, p.id);
    const min = threshold(s, p);
    const st = statusOf(t.available, min);
    if (st === "IN_STOCK") continue;
    const unit = unitOf(s, p).code;
    s.notifications.push({
      id: id++,
      type: st,
      title: st === "OUT_OF_STOCK" ? `${p.name} is out of stock` : `${p.name} is running low`,
      body: st === "OUT_OF_STOCK" ? "No stock available." : `Only ${t.available} ${unit} left (minimum ${min}).`,
      payload: { productId: p.id, variantId: p.id },
      read: false,
      createdAt: p.updatedAt,
    });
  }
  s.notifications.push({
    id: id++,
    type: "TRADE_INVOICE_RECEIVED",
    title: "New purchase invoice from Metro Cable Mills (sample)",
    body: "MC-2026-00418 needs your approval.",
    payload: { tradeInvoiceId: 4 },
    read: false,
    createdAt: daysAgo(0, 8, 10),
  });
  s.notifications.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}
