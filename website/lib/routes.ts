import type { Permission } from "./api/types";

/**
 * Dashboard screens. Live (/dashboard/...) and demo (/dashboard/demo/...) share this table, so
 * both modes always expose exactly the same screens.
 */
export type ScreenKey =
  | "overview"
  | "products"
  | "product-new"
  | "product-detail"
  | "catalog"
  | "stock"
  | "warehouses"
  | "sales"
  | "sale-new"
  | "sale-detail"
  | "purchases"
  | "purchase-new"
  | "purchase-detail"
  | "customers"
  | "suppliers"
  | "invoices"
  | "invoice-new"
  | "invoice-detail"
  | "reports"
  | "employees"
  | "settings"
  | "profile"
  | "notifications"
  | "product-edit"
  | "stock-counts"
  | "stock-count-detail"
  | "warehouse-detail"
  | "sale-return"
  | "sale-receipt"
  | "invoice-edit"
  | "network"
  | "product-links"
  | "employee-detail"
  | "backup"
  | "appearance";

export interface ScreenMatch {
  key: ScreenKey;
  title: string;
  id?: number;
}

const STATIC: Record<string, { key: ScreenKey; title: string }> = {
  "": { key: "overview", title: "Overview" },
  products: { key: "products", title: "Products" },
  "products/new": { key: "product-new", title: "Add product" },
  catalog: { key: "catalog", title: "Categories & fields" },
  stock: { key: "stock", title: "Inventory" },
  warehouses: { key: "warehouses", title: "Warehouses & branches" },
  sales: { key: "sales", title: "Sales" },
  "sales/new": { key: "sale-new", title: "Record sale" },
  purchases: { key: "purchases", title: "Purchases" },
  "purchases/new": { key: "purchase-new", title: "Record purchase" },
  customers: { key: "customers", title: "Customers" },
  suppliers: { key: "suppliers", title: "Suppliers" },
  invoices: { key: "invoices", title: "Business invoices" },
  "invoices/new": { key: "invoice-new", title: "New invoice" },
  reports: { key: "reports", title: "Reports" },
  employees: { key: "employees", title: "Employees" },
  settings: { key: "settings", title: "Business settings" },
  profile: { key: "profile", title: "Profile" },
  notifications: { key: "notifications", title: "Notifications" },
  "stock-counts": { key: "stock-counts", title: "Stock counts" },
  network: { key: "network", title: "Business network" },
  "product-links": { key: "product-links", title: "Product links" },
  backup: { key: "backup", title: "Backup & restore" },
  appearance: { key: "appearance", title: "Appearance & language" },
};

const DETAIL: Record<string, { key: ScreenKey; title: string }> = {
  products: { key: "product-detail", title: "Product" },
  sales: { key: "sale-detail", title: "Sale" },
  purchases: { key: "purchase-detail", title: "Purchase" },
  invoices: { key: "invoice-detail", title: "B2B invoice" },
  "stock-counts": { key: "stock-count-detail", title: "Stock count" },
  warehouses: { key: "warehouse-detail", title: "Warehouse" },
  employees: { key: "employee-detail", title: "Employee" },
};

/** /section/:id/action screens. */
const ACTION: Record<string, { key: ScreenKey; title: string }> = {
  "products/edit": { key: "product-edit", title: "Edit product" },
  "sales/return": { key: "sale-return", title: "Record return" },
  "sales/receipt": { key: "sale-receipt", title: "Receipt" },
  "invoices/edit": { key: "invoice-edit", title: "Edit draft invoice" },
};

export function matchScreen(slug: string[] | undefined): ScreenMatch | null {
  const parts = slug ?? [];
  const joined = parts.join("/");
  // Own-property checks, so slugs like "constructor" or "__proto__" don't match Object.prototype members.
  if (Object.hasOwn(STATIC, joined)) return STATIC[joined];
  if (parts.length === 2 && Object.hasOwn(DETAIL, parts[0]) && /^\d+$/.test(parts[1])) {
    return { ...DETAIL[parts[0]], id: Number(parts[1]) };
  }
  if (parts.length === 3 && /^\d+$/.test(parts[1]) && Object.hasOwn(ACTION, `${parts[0]}/${parts[2]}`)) {
    return { ...ACTION[`${parts[0]}/${parts[2]}`], id: Number(parts[1]) };
  }
  return null;
}

export interface NavItem {
  path: string;
  label: string;
  icon: string;
  /** Visible when the member has any of these (empty = everyone). */
  any: Permission[];
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

/** Mirrors the Android tabs and More menu (ui/settings/MoreScreen.kt), with the same gating. */
export const NAV: NavGroup[] = [
  {
    label: "Inventory",
    items: [
      { path: "", label: "Overview", icon: "LayoutDashboard", any: [] },
      { path: "/products", label: "Products", icon: "Package", any: [] },
      { path: "/catalog", label: "Categories & fields", icon: "Tags", any: [] },
      { path: "/stock", label: "Inventory", icon: "Boxes", any: [] },
      { path: "/warehouses", label: "Warehouses & branches", icon: "Warehouse", any: [] },
      { path: "/stock-counts", label: "Stock counts", icon: "ClipboardCheck", any: ["STOCK_COUNT"] },
    ],
  },
  {
    label: "Trade",
    items: [
      { path: "/sales", label: "Sales", icon: "ShoppingCart", any: ["SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW"] },
      { path: "/purchases", label: "Purchases", icon: "Truck", any: ["PURCHASE_MANAGE"] },
      { path: "/customers", label: "Customers", icon: "Users", any: ["SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW"] },
      { path: "/suppliers", label: "Suppliers", icon: "Factory", any: ["PURCHASE_MANAGE", "PRODUCT_EDIT", "PRODUCT_CREATE"] },
    ],
  },
  {
    label: "Business network",
    items: [
      { path: "/network", label: "Connected businesses", icon: "Network", any: ["SALE_RECORD", "PURCHASE_MANAGE", "BUSINESS_SETTINGS"] },
      { path: "/invoices", label: "Business invoices", icon: "FileText", any: ["SALE_RECORD", "PURCHASE_MANAGE"] },
      { path: "/product-links", label: "Product links", icon: "Link2", any: ["PURCHASE_MANAGE"] },
    ],
  },
  {
    label: "Insights",
    items: [{ path: "/reports", label: "Reports", icon: "BarChart3", any: ["REPORT_VIEW", "ANALYTICS_VIEW"] }],
  },
  {
    label: "Business",
    items: [
      { path: "/employees", label: "Employees", icon: "UserCog", any: ["EMPLOYEE_MANAGE"] },
      { path: "/settings", label: "Business settings", icon: "Settings", any: [] },
      { path: "/backup", label: "Backup & restore", icon: "DatabaseBackup", any: [] },
    ],
  },
  {
    label: "App",
    items: [
      { path: "/appearance", label: "Appearance & language", icon: "Palette", any: [] },
      { path: "/notifications", label: "Notifications", icon: "Bell", any: [] },
      { path: "/profile", label: "Profile", icon: "CircleUser", any: [] },
    ],
  },
];
