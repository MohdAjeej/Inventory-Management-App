import type { Me, Permission, Role } from "./api/types";

/**
 * The UI gates on effective permissions (like the Android app's `me.can()`), never on role names.
 * The backend enforces every permission regardless; hiding controls is only for clarity.
 */
export function can(me: Pick<Me, "permissions"> | null | undefined, ...perms: Permission[]): boolean {
  if (!me) return false;
  return perms.some((p) => me.permissions.includes(p));
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Business Admin",
  INVENTORY_MANAGER: "Inventory Manager",
  STAFF: "Staff",
};

/** Role defaults from GET /api/permissions (security/RolePermissions.java). */
export const ROLE_DEFAULTS: Record<Role, Permission[]> = {
  ADMIN: [
    "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_ARCHIVE", "STOCK_ADD", "STOCK_REDUCE",
    "STOCK_ADJUST", "STOCK_TRANSFER", "STOCK_COUNT", "REPORT_VIEW", "REPORT_EXPORT", "CATEGORY_MANAGE",
    "WAREHOUSE_MANAGE", "EMPLOYEE_MANAGE", "BUSINESS_SETTINGS", "SALE_RECORD", "SALE_CANCEL",
    "ANALYTICS_VIEW", "PURCHASE_MANAGE",
  ],
  INVENTORY_MANAGER: [
    "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_EDIT", "PRODUCT_ARCHIVE", "STOCK_ADD", "STOCK_REDUCE",
    "STOCK_ADJUST", "STOCK_TRANSFER", "STOCK_COUNT", "REPORT_VIEW", "REPORT_EXPORT", "CATEGORY_MANAGE",
    "WAREHOUSE_MANAGE", "SALE_RECORD", "SALE_CANCEL", "ANALYTICS_VIEW", "PURCHASE_MANAGE",
  ],
  STAFF: ["PRODUCT_VIEW", "STOCK_ADD", "STOCK_REDUCE", "STOCK_COUNT", "SALE_RECORD"],
};
