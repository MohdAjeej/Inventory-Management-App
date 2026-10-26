/**
 * Typed wrappers around the documented REST API (docs/API.md). Every function takes an
 * ApiClient so the same call works against the live backend or the demo data provider.
 * No business id is ever sent: the backend derives the business from the session token.
 */
import type { ApiClient, Query } from "./client";
import type * as T from "./types";

export const api = {
  me: (c: ApiClient) => c.request<T.Me>("GET", "/auth/me"),
  updateMe: (c: ApiClient, body: { fullName: string; phone?: string }) => c.request<T.Me>("PUT", "/auth/me", { body }),
  changePassword: (c: ApiClient, body: { currentPassword: string; newPassword: string }) =>
    c.request<void>("POST", "/auth/change-password", { body }),

  dashboard: (c: ApiClient) => c.request<T.Dashboard>("GET", "/dashboard"),

  business: {
    get: (c: ApiClient) => c.request<T.Business>("GET", "/business"),
    update: (c: ApiClient, body: Partial<T.Business> & { name: string }) => c.request<T.Business>("PUT", "/business", { body }),
    settings: (c: ApiClient) => c.request<T.BusinessSettings>("GET", "/business/settings"),
    updateSettings: (c: ApiClient, body: T.BusinessSettings) =>
      c.request<T.BusinessSettings>("PUT", "/business/settings", { body }),
  },

  catalog: {
    units: (c: ApiClient) => c.request<T.Unit[]>("GET", "/units"),
    categories: (c: ApiClient, includeArchived = false) =>
      c.request<T.Category[]>("GET", "/categories", { query: { includeArchived } }),
    attributes: (c: ApiClient) => c.request<T.Attribute[]>("GET", "/attributes"),
  },

  warehouses: (c: ApiClient, includeArchived = false) =>
    c.request<T.Warehouse[]>("GET", "/warehouses", { query: { includeArchived } }),

  products: {
    list: (c: ApiClient, q: Query) => c.request<T.Page<T.ProductSummary>>("GET", "/products", { query: q }),
    get: (c: ApiClient, id: number) => c.request<T.ProductDetail>("GET", `/products/${id}`),
    create: (c: ApiClient, body: T.ProductRequest) => c.request<T.ProductDetail>("POST", "/products", { body }),
    archive: (c: ApiClient, id: number) => c.request<T.ProductDetail>("POST", `/products/${id}/archive`),
    restore: (c: ApiClient, id: number) => c.request<T.ProductDetail>("POST", `/products/${id}/restore`),
  },

  stock: {
    operate: (c: ApiClient, body: T.StockOperationRequest) =>
      c.request<T.OperationResult>("POST", "/stock/operations", { body }),
    transfer: (c: ApiClient, body: T.TransferRequest) => c.request<T.OperationResult>("POST", "/stock/transfers", { body }),
    movements: (c: ApiClient, q: Query) => c.request<T.Page<T.Movement>>("GET", "/stock/movements", { query: q }),
  },

  reports: {
    inventory: (c: ApiClient, q: Query) => c.request<T.Page<T.InventoryRow>>("GET", "/reports/inventory", { query: q }),
    lowStock: (c: ApiClient, q: Query) => c.request<T.Page<T.InventoryRow>>("GET", "/reports/low-stock", { query: q }),
    movementSummary: (c: ApiClient, q: Query) =>
      c.request<T.MovementSummary>("GET", "/reports/movement-summary", { query: q }),
  },

  analytics: {
    overview: (c: ApiClient, q: Query) => c.request<T.AnalyticsOverview>("GET", "/analytics/overview", { query: q }),
    products: (c: ApiClient, q: Query) =>
      c.request<{ period: T.Period; products: T.ProductPerformance[] }>("GET", "/analytics/products", { query: q }),
    trend: (c: ApiClient, q: Query) => c.request<T.Trend>("GET", "/analytics/trend", { query: q }),
    categories: (c: ApiClient, q: Query) =>
      c.request<{ period: T.Period; categories: T.CategoryPerformance[] }>("GET", "/analytics/categories", { query: q }),
    insights: (c: ApiClient, q: Query) =>
      c.request<{ period: T.Period; insights: T.Insight[] }>("GET", "/analytics/insights", { query: q }),
  },

  sales: {
    list: (c: ApiClient, q: Query) => c.request<T.Page<T.SaleSummary>>("GET", "/sales", { query: q }),
    get: (c: ApiClient, id: number) => c.request<T.Sale>("GET", `/sales/${id}`),
    create: (c: ApiClient, body: T.SaleRequest) => c.request<T.Sale>("POST", "/sales", { body }),
    addPayment: (c: ApiClient, id: number, body: { amount: number; method: string; reference?: string; clientRequestId: string }) =>
      c.request<T.Sale>("POST", `/sales/${id}/payments`, { body }),
    cancel: (c: ApiClient, id: number, body: { reason: string; refundMethod?: string }) =>
      c.request<T.Sale>("POST", `/sales/${id}/cancel`, { body }),
  },

  purchases: {
    list: (c: ApiClient, q: Query) => c.request<T.Page<T.PurchaseSummary>>("GET", "/purchases", { query: q }),
    get: (c: ApiClient, id: number) => c.request<T.Purchase>("GET", `/purchases/${id}`),
    create: (c: ApiClient, body: T.PurchaseRequest) => c.request<T.Purchase>("POST", "/purchases", { body }),
    addPayment: (c: ApiClient, id: number, body: { amount: number; method: string; reference?: string; clientRequestId: string }) =>
      c.request<T.Purchase>("POST", `/purchases/${id}/payments`, { body }),
    cancel: (c: ApiClient, id: number, body: { reason: string }) =>
      c.request<T.Purchase>("POST", `/purchases/${id}/cancel`, { body }),
  },

  parties: {
    list: (c: ApiClient, kind: PartyKind, q: Query) => c.request<T.Party[]>("GET", `/${kind}`, { query: q }),
    create: (c: ApiClient, kind: PartyKind, body: T.PartyRequest) => c.request<T.Party>("POST", `/${kind}`, { body }),
    update: (c: ApiClient, kind: PartyKind, id: number, body: T.PartyRequest) =>
      c.request<T.Party>("PUT", `/${kind}/${id}`, { body }),
    archive: (c: ApiClient, kind: PartyKind, id: number) => c.request<T.Party>("POST", `/${kind}/${id}/archive`),
    restore: (c: ApiClient, kind: PartyKind, id: number) => c.request<T.Party>("POST", `/${kind}/${id}/restore`),
  },

  network: {
    me: (c: ApiClient) => c.request<{ businessCode: string; name: string; discoverable: boolean }>("GET", "/network/me"),
    connections: (c: ApiClient, status?: string) =>
      c.request<T.Connection[]>("GET", "/network/connections", { query: { status } }),
  },

  trade: {
    list: (c: ApiClient, q: Query) => c.request<T.Page<T.TradeInvoiceSummary>>("GET", "/trade/invoices", { query: q }),
    get: (c: ApiClient, id: number) => c.request<T.TradeInvoice>("GET", `/trade/invoices/${id}`),
    create: (c: ApiClient, body: T.TradeInvoiceRequest) => c.request<T.TradeInvoice>("POST", "/trade/invoices", { body }),
    send: (c: ApiClient, id: number) => c.request<T.TradeInvoice>("POST", `/trade/invoices/${id}/send`),
    cancel: (c: ApiClient, id: number, reason: string) =>
      c.request<T.TradeInvoice>("POST", `/trade/invoices/${id}/cancel`, { body: { reason } }),
    reject: (c: ApiClient, id: number, reason: string) =>
      c.request<T.TradeInvoice>("POST", `/trade/invoices/${id}/reject`, { body: { reason } }),
    remove: (c: ApiClient, id: number) => c.request<void>("DELETE", `/trade/invoices/${id}`),
    preview: (c: ApiClient, id: number, warehouseId?: number) =>
      c.request<T.ReceivePreview>("GET", `/trade/invoices/${id}/preview`, { query: { warehouseId } }),
    approve: (
      c: ApiClient,
      id: number,
      body: { warehouseId?: number; lines: { lineId: number; variantId?: number; createNew: boolean }[] },
    ) => c.request<T.TradeInvoice>("POST", `/trade/invoices/${id}/approve`, { body }),
    summary: (c: ApiClient) => c.request<T.TradeSummary>("GET", "/trade/summary"),
  },

  employees: {
    list: (c: ApiClient, q: Query) => c.request<T.Page<T.Employee>>("GET", "/employees", { query: q }),
    setRole: (c: ApiClient, id: number, role: T.Role) => c.request<T.Employee>("PUT", `/employees/${id}/role`, { body: { role } }),
    deactivate: (c: ApiClient, id: number) => c.request<T.Employee>("POST", `/employees/${id}/deactivate`),
    reactivate: (c: ApiClient, id: number) => c.request<T.Employee>("POST", `/employees/${id}/reactivate`),
    permissions: (c: ApiClient) => c.request<T.PermissionCatalog>("GET", "/permissions"),
    invitations: (c: ApiClient) => c.request<T.Page<T.Invitation>>("GET", "/invitations", { query: { status: "PENDING" } }),
    invite: (c: ApiClient, body: { email: string; role: T.Role }) => c.request<T.Invitation>("POST", "/invitations", { body }),
    cancelInvitation: (c: ApiClient, id: number) => c.request<void>("DELETE", `/invitations/${id}`),
  },

  notifications: {
    list: (c: ApiClient, q: Query) => c.request<T.Page<T.Notification>>("GET", "/notifications", { query: q }),
    markRead: (c: ApiClient, id: number) => c.request<void>("POST", `/notifications/${id}/read`),
    markAllRead: (c: ApiClient) => c.request<void>("POST", "/notifications/read-all"),
  },
  // ---- parity with the Android app ----
  catalogAdmin: {
    createUnit: (c: ApiClient, body: { code: string; name: string; decimals: number }) => c.request<T.Unit>("POST", "/units", { body }),
    updateUnit: (c: ApiClient, id: number, body: { code: string; name: string; decimals: number }) => c.request<T.Unit>("PUT", `/units/${id}`, { body }),
    setUnitArchived: (c: ApiClient, id: number, archived: boolean) => c.request<T.Unit>("POST", `/units/${id}/${archived ? "archive" : "restore"}`),
    units: (c: ApiClient) => c.request<T.Unit[]>("GET", "/units", { query: { includeArchived: true } }),
    conversions: (c: ApiClient) => c.request<T.UnitConversion[]>("GET", "/unit-conversions"),
    createConversion: (c: ApiClient, body: { fromUnitId: number; toUnitId: number; factor: number; productId?: number | null }) =>
      c.request<T.UnitConversion>("POST", "/unit-conversions", { body }),
    deleteConversion: (c: ApiClient, id: number) => c.request<void>("DELETE", `/unit-conversions/${id}`),
    convert: (c: ApiClient, q: Query) =>
      c.request<{ quantity: number; fromUnit: string; result: number; toUnit: string }>("GET", "/unit-conversions/convert", { query: q }),
    createCategory: (c: ApiClient, body: { name: string; description?: string; parentId?: number | null }) =>
      c.request<T.Category>("POST", "/categories", { body }),
    updateCategory: (c: ApiClient, id: number, body: { name: string; description?: string; parentId?: number | null }) =>
      c.request<T.Category>("PUT", `/categories/${id}`, { body }),
    setCategoryArchived: (c: ApiClient, id: number, archived: boolean) =>
      c.request<T.Category>("POST", `/categories/${id}/${archived ? "archive" : "restore"}`),
    attributes: (c: ApiClient, includeArchived = true) => c.request<T.Attribute[]>("GET", "/attributes", { query: { includeArchived } }),
    createAttribute: (c: ApiClient, body: T.AttributeRequest) => c.request<T.Attribute>("POST", "/attributes", { body }),
    updateAttribute: (c: ApiClient, id: number, body: T.AttributeRequest) => c.request<T.Attribute>("PUT", `/attributes/${id}`, { body }),
    setAttributeArchived: (c: ApiClient, id: number, archived: boolean) =>
      c.request<T.Attribute>("POST", `/attributes/${id}/${archived ? "archive" : "restore"}`),
    formSchema: (c: ApiClient, categoryId?: number | null) =>
      c.request<T.FormSchema>("GET", "/form-schema", { query: { categoryId: categoryId ?? undefined } }),
  },

  productAdmin: {
    update: (c: ApiClient, id: number, body: T.ProductRequest) => c.request<T.ProductDetail>("PUT", `/products/${id}`, { body }),
    addVariant: (c: ApiClient, productId: number, body: T.VariantRequest) =>
      c.request<T.ProductDetail>("POST", `/products/${productId}/variants`, { body }),
    updateVariant: (c: ApiClient, variantId: number, body: T.VariantRequest) =>
      c.request<T.ProductDetail>("PUT", `/variants/${variantId}`, { body }),
    setVariantArchived: (c: ApiClient, variantId: number, archived: boolean) =>
      c.request<T.ProductDetail>("POST", `/variants/${variantId}/${archived ? "archive" : "restore"}`),
    deleteImage: (c: ApiClient, productId: number, imageId: number) =>
      c.request<T.ProductDetail>("DELETE", `/products/${productId}/images/${imageId}`),
    batches: (c: ApiClient, variantId: number) => c.request<T.Batch[]>("GET", `/variants/${variantId}/batches`),
    serials: (c: ApiClient, variantId: number) => c.request<T.Serial[]>("GET", `/variants/${variantId}/serials`),
    lookup: (c: ApiClient, code: string) => c.request<T.ProductSummary[]>("GET", "/products/lookup", { query: { code } }),
  },

  warehouseAdmin: {
    create: (c: ApiClient, body: T.WarehouseRequest) => c.request<T.Warehouse>("POST", "/warehouses", { body }),
    update: (c: ApiClient, id: number, body: T.WarehouseRequest) => c.request<T.Warehouse>("PUT", `/warehouses/${id}`, { body }),
    setArchived: (c: ApiClient, id: number, archived: boolean) =>
      c.request<T.Warehouse>("POST", `/warehouses/${id}/${archived ? "archive" : "restore"}`),
    makeDefault: (c: ApiClient, id: number) => c.request<T.Warehouse>("POST", `/warehouses/${id}/default`),
    locations: (c: ApiClient, id: number) => c.request<T.Location[]>("GET", `/warehouses/${id}/locations`),
    createLocation: (c: ApiClient, id: number, body: { code: string; description?: string }) =>
      c.request<T.Location>("POST", `/warehouses/${id}/locations`, { body }),
    updateLocation: (c: ApiClient, id: number, body: { code: string; description?: string }) =>
      c.request<T.Location>("PUT", `/locations/${id}`, { body }),
    setLocationArchived: (c: ApiClient, id: number, archived: boolean) =>
      c.request<T.Location>("POST", `/locations/${id}/${archived ? "archive" : "restore"}`),
  },

  counts: {
    list: (c: ApiClient, q: Query) => c.request<T.Page<T.StockCountSummary>>("GET", "/stock-counts", { query: q }),
    get: (c: ApiClient, id: number) => c.request<T.StockCount>("GET", `/stock-counts/${id}`),
    create: (c: ApiClient, body: { warehouseId: number; categoryId?: number | null; notes?: string }) =>
      c.request<T.StockCount>("POST", "/stock-counts", { body }),
    updateLines: (c: ApiClient, id: number, lines: { lineId: number; countedQty: number | null }[]) =>
      c.request<T.StockCount>("PUT", `/stock-counts/${id}/lines`, { body: { lines } }),
    reconcile: (c: ApiClient, id: number) =>
      c.request<{ summary: T.StockCountSummary; adjustedLines: number }>("POST", `/stock-counts/${id}/reconcile`),
    cancel: (c: ApiClient, id: number) => c.request<T.StockCountSummary>("POST", `/stock-counts/${id}/cancel`),
  },

  saleReturn: (c: ApiClient, id: number, body: T.SaleReturnRequest) => c.request<T.Sale>("POST", `/sales/${id}/returns`, { body }),

  networkAdmin: {
    search: (c: ApiClient, q: string) => c.request<T.SearchResult[]>("GET", "/network/search", { query: { q } }),
    setDiscoverable: (c: ApiClient, discoverable: boolean) =>
      c.request<{ businessCode: string; name: string; discoverable: boolean }>("PUT", "/network/me/discoverable", { body: { discoverable } }),
    connect: (c: ApiClient, businessId: number, message?: string) =>
      c.request<T.Connection>("POST", "/network/connections", { body: { businessId, message } }),
    respond: (c: ApiClient, id: number, action: "accept" | "reject" | "remove") =>
      c.request<T.Connection>("POST", `/network/connections/${id}/${action}`),
  },

  tradeAdmin: {
    update: (c: ApiClient, id: number, body: T.TradeInvoiceRequest) => c.request<T.TradeInvoice>("PUT", `/trade/invoices/${id}`, { body }),
    requestReturn: (
      c: ApiClient,
      id: number,
      body: { lines: { invoiceLineId: number; quantity: number }[]; reason: string; notes?: string; clientRequestId: string },
    ) => c.request<T.TradeInvoice>("POST", `/trade/invoices/${id}/returns`, { body }),
    acceptReturn: (c: ApiClient, returnId: number, note?: string) =>
      c.request<T.TradeInvoice>("POST", `/trade/returns/${returnId}/accept`, { body: { note } }),
    rejectReturn: (c: ApiClient, returnId: number, note?: string) =>
      c.request<T.TradeInvoice>("POST", `/trade/returns/${returnId}/reject`, { body: { note } }),
    cancelReturn: (c: ApiClient, returnId: number) => c.request<T.TradeInvoice>("POST", `/trade/returns/${returnId}/cancel`),
    mappings: (c: ApiClient) => c.request<T.ProductMapping[]>("GET", "/trade/mappings"),
    deleteMapping: (c: ApiClient, id: number) => c.request<void>("DELETE", `/trade/mappings/${id}`),
  },

  employeeAdmin: {
    get: (c: ApiClient, id: number) => c.request<T.Employee>("GET", `/employees/${id}`),
    setOverrides: (c: ApiClient, id: number, overrides: Partial<Record<T.Permission, boolean>>) =>
      c.request<T.Employee>("PUT", `/employees/${id}/permissions`, { body: { overrides } }),
  },
};

export type PartyKind = "customers" | "suppliers";
