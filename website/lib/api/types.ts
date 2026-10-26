/**
 * Typed models mirroring the Spring Boot DTOs in backend/src/main/java/com/b2binventory/dto.
 * The backend serialises with `non_null`, so most fields are optional. Money and quantities
 * arrive as JSON numbers; instants are ISO-8601 UTC strings; LocalDate is YYYY-MM-DD.
 */

export type Permission =
  | "PRODUCT_VIEW"
  | "PRODUCT_CREATE"
  | "PRODUCT_EDIT"
  | "PRODUCT_ARCHIVE"
  | "STOCK_ADD"
  | "STOCK_REDUCE"
  | "STOCK_ADJUST"
  | "STOCK_TRANSFER"
  | "STOCK_COUNT"
  | "REPORT_VIEW"
  | "REPORT_EXPORT"
  | "CATEGORY_MANAGE"
  | "WAREHOUSE_MANAGE"
  | "EMPLOYEE_MANAGE"
  | "BUSINESS_SETTINGS"
  | "SALE_RECORD"
  | "SALE_CANCEL"
  | "ANALYTICS_VIEW"
  | "PURCHASE_MANAGE";

export type Role = "ADMIN" | "INVENTORY_MANAGER" | "STAFF";

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
  timestamp?: string;
}

// ---------------------------------------------------------------- auth

export interface Membership {
  membershipId: number;
  businessId: number;
  businessName: string;
  role: Role;
  businessType?: string;
}

export interface Me {
  userId: number;
  fullName: string;
  email: string;
  phone?: string;
  membershipId: number;
  businessId: number;
  businessName: string;
  role: Role;
  permissions: Permission[];
  memberships: Membership[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: Me;
}

export interface RegisterRequest {
  businessName: string;
  businessType?: string;
  businessEmail?: string;
  businessPhone?: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  fullName: string;
  email: string;
  phone?: string;
  password: string;
  timezone?: string;
}

export interface InvitationPreview {
  businessName: string;
  email: string;
  role: Role;
  existingUser: boolean;
  expiresAt: string;
}

// ---------------------------------------------------------------- business

export interface Business {
  id: number;
  name: string;
  businessType?: string;
  logoUrl?: string;
  email?: string;
  phone?: string;
  address?: string;
  city?: string;
  state?: string;
  country?: string;
  postalCode?: string;
  createdAt?: string;
  businessCode?: string;
  taxId?: string;
}

export interface BusinessSettings {
  allowNegativeStock: boolean;
  defaultLowStockThreshold: number;
  trackBatches: boolean;
  trackExpiry: boolean;
  trackSerials: boolean;
  trackDamagedReserved: boolean;
  notifyLowStock: boolean;
  timezone?: string;
  currencyCode?: string;
}

export interface PermissionCatalog {
  permissions: { permission: Permission; label: string }[];
  roleDefaults: Record<Role, Permission[]>;
}

export interface Employee {
  membershipId: number;
  userId: number;
  fullName: string;
  email: string;
  phone?: string;
  role: Role;
  status: "ACTIVE" | "DEACTIVATED";
  permissions: Permission[];
  overrides: Partial<Record<Permission, boolean>>;
  joinedAt?: string;
}

export interface Invitation {
  id: number;
  email: string;
  role: Role;
  code: string;
  status: "PENDING" | "ACCEPTED" | "CANCELLED" | "EXPIRED";
  invitedBy?: string;
  expiresAt: string;
  createdAt: string;
}

// ---------------------------------------------------------------- catalog

export interface Unit {
  id: number;
  code: string;
  name: string;
  decimals: number;
  archived: boolean;
}

export interface Category {
  id: number;
  name: string;
  description?: string;
  parentId?: number;
  path: string;
  archived: boolean;
  productCount: number;
  depth: number;
  children: Category[];
}

export type FieldType = "TEXT" | "NUMBER" | "DATE" | "BOOLEAN" | "DROPDOWN" | "MULTI_SELECT";

export interface Attribute {
  id: number;
  key: string;
  label: string;
  fieldType: FieldType;
  required: boolean;
  options?: string[];
  sortOrder?: number;
  categoryId?: number;
  categoryName?: string;
  archived: boolean;
}

export interface Warehouse {
  id: number;
  name: string;
  code: string;
  address?: string;
  branch: boolean;
  isDefault: boolean;
  archived: boolean;
  productCount: number;
  totalQuantity: number;
}

// ---------------------------------------------------------------- products & stock

export type StockStatus = "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK";

export interface StockTotals {
  onHand: number;
  reserved: number;
  damaged: number;
  available: number;
}

export interface ProductSummary {
  id: number;
  name: string;
  brand?: string;
  model?: string;
  categoryId?: number;
  categoryName?: string;
  unitCode: string;
  sku?: string;
  barcode?: string;
  imageUrl?: string;
  stock: StockTotals;
  effectiveMinStock: number;
  status: StockStatus;
  variantCount: number;
  archived: boolean;
  updatedAt: string;
  salePrice?: number;
  purchasePrice?: number;
}

export interface Variant {
  id: number;
  name: string;
  sku?: string;
  barcode?: string;
  attributes?: Record<string, unknown>;
  minStock?: number;
  effectiveMinStock: number;
  isDefault: boolean;
  archived: boolean;
  stock: StockTotals;
  status: StockStatus;
}

export interface Balance {
  warehouseId: number;
  warehouseName: string;
  variantId: number;
  variantName: string;
  batchId?: number;
  lotNumber?: string;
  expiryDate?: string;
  onHand: number;
  reserved: number;
  damaged: number;
  available: number;
}

export interface Pricing {
  unitCost?: number;
  costSource?: "AVERAGE" | "PURCHASE_PRICE";
  profitPerUnit?: number;
  marginPercent?: number;
  stockValueAtCost?: number;
  stockValueAtSellingPrice?: number;
  potentialProfit?: number;
}

export interface ProductDetail {
  id: number;
  name: string;
  brand?: string;
  model?: string;
  description?: string;
  categoryId?: number;
  categoryName?: string;
  categoryPath?: string;
  baseUnit: Unit;
  minStock?: number;
  effectiveMinStock: number;
  attributes: Record<string, unknown>;
  trackBatches: boolean;
  trackSerials: boolean;
  archived: boolean;
  stock: StockTotals;
  status: StockStatus;
  variants: Variant[];
  images: { id: number; url: string }[];
  balances: Balance[];
  createdAt: string;
  updatedAt: string;
  salePrice?: number;
  purchasePrice?: number;
  averageCost?: number;
  taxRate?: number;
  supplierId?: number;
  supplierName?: string;
  pricing?: Pricing;
}

export interface ProductRequest {
  name: string;
  categoryId?: number | null;
  baseUnitId?: number | null;
  brand?: string;
  model?: string;
  description?: string;
  minStock?: number | null;
  attributes?: Record<string, unknown>;
  sku?: string;
  barcode?: string;
  trackBatches?: boolean;
  trackSerials?: boolean;
  openingStock?: { quantity: number; warehouseId?: number | null; lotNumber?: string; expiryDate?: string; serials?: string[] } | null;
  salePrice?: number | null;
  purchasePrice?: number | null;
  taxRate?: number | null;
  supplierId?: number | null;
}

export type MovementType =
  | "OPENING"
  | "ADD"
  | "REDUCE"
  | "ADJUST"
  | "TRANSFER_OUT"
  | "TRANSFER_IN"
  | "COUNT_ADJUST"
  | "DAMAGE"
  | "RESERVE"
  | "RELEASE"
  | "SALE"
  | "SALE_CANCEL"
  | "RETURN_IN"
  | "PURCHASE"
  | "PURCHASE_CANCEL"
  | "PURCHASE_RETURN";

export interface Movement {
  id: number;
  type: MovementType;
  bucket?: string;
  productId: number;
  productName: string;
  variantId: number;
  variantName?: string;
  sku?: string;
  warehouseId: number;
  warehouseName: string;
  batchId?: number;
  lotNumber?: string;
  quantity: number;
  quantityBefore: number;
  quantityAfter: number;
  unitCode: string;
  reason?: string;
  notes?: string;
  actorId?: number;
  actorName?: string;
  transferGroupId?: string;
  createdAt: string;
}

export type OperationType = "ADD" | "REDUCE" | "ADJUST" | "DAMAGE" | "RESERVE" | "RELEASE";

export interface StockOperationRequest {
  type: OperationType;
  variantId: number;
  warehouseId?: number | null;
  quantity: number;
  reason?: string;
  notes?: string;
  batchId?: number | null;
  lotNumber?: string;
  expiryDate?: string;
  serials?: string[] | null;
  fromDamaged?: boolean;
  clientRequestId?: string;
}

export interface TransferRequest {
  variantId: number;
  fromWarehouseId: number;
  toWarehouseId: number;
  quantity: number;
  batchId?: number | null;
  serials?: string[] | null;
  reason?: string;
  notes?: string;
  clientRequestId?: string;
}

export interface OperationResult {
  movements: Movement[];
  balances: Balance[];
}

// ---------------------------------------------------------------- dashboard & reports

export interface StockItem {
  productId: number;
  productName: string;
  unit: string;
  available: number;
  minStock?: number;
  status: StockStatus;
  updatedAt?: string;
}

export interface Bucket {
  categoryId?: number;
  categoryName?: string;
  warehouseId?: number;
  warehouseName?: string;
  productCount: number;
  quantity: number;
}

export interface SalesSnapshot {
  sales: number;
  units: number;
  revenue: number;
  grossProfit?: number;
  currencyCode: string;
}

export interface ProductRef {
  productId: number;
  name: string;
  unit: string;
  units: number;
  revenue: number;
}

export interface InventoryValue {
  valueAtCost?: number;
  valueAtSellingPrice?: number;
  potentialProfit?: number;
  productsWithoutCost: number;
  productsWithoutPrice: number;
}

export interface Dashboard {
  activeProducts: number;
  totalQuantity: number;
  lowStockCount: number;
  outOfStockCount: number;
  lowStockItems: StockItem[];
  outOfStockItems: StockItem[];
  recentMovements: Movement[];
  categories: Bucket[];
  warehouses: Bucket[];
  recentlyUpdated?: StockItem[];
  mostStocked?: StockItem[];
  unreadNotifications: number;
  sales?: {
    today: SalesSnapshot;
    month: SalesSnapshot;
    monthBestSeller?: ProductRef;
    monthLowestSeller?: ProductRef;
    monthProductsWithNoSales: number;
    inventory: InventoryValue;
  };
}

export interface InventoryRow {
  productId: number;
  variantId: number;
  productName: string;
  variantName?: string;
  sku?: string;
  barcode?: string;
  category?: string;
  unit: string;
  onHand: number;
  reserved: number;
  damaged: number;
  available: number;
  minStock?: number;
  status: StockStatus;
}

export interface MovementSummary {
  from: string;
  to: string;
  totalIn: number;
  totalOut: number;
  movementCount: number;
  byType: { type: MovementType; count: number; quantity: number }[];
  fastMoving: { productId: number; productName: string; unit: string; quantityOut: number; available: number }[];
  slowMoving: { productId: number; productName: string; unit: string; quantityOut: number; available: number }[];
}

export interface Notification {
  id: number;
  type: string;
  title: string;
  body?: string;
  payload?: Record<string, unknown>;
  read: boolean;
  createdAt: string;
}

// ---------------------------------------------------------------- analytics

export interface Period {
  from: string;
  to: string;
  days: number;
  previousFrom: string;
  previousTo: string;
  timezone: string;
  currencyCode: string;
}

export interface Metric {
  value?: number;
  previous?: number;
  growthPercent?: number;
}

export interface AnalyticsOverview {
  period: Period;
  sales: Metric;
  units: Metric;
  revenue: Metric;
  grossProfit: Metric;
  financials: {
    grossSales: number;
    discounts: number;
    returns: number;
    netSales: number;
    taxCollected: number;
    costOfGoodsSold?: number;
    grossProfit?: number;
    marginPercent?: number;
    profitAvailable: boolean;
    linesWithoutCost: number;
  };
  averageDailyUnits: number;
  averageDailyRevenue: number;
  bestSeller?: ProductRef;
  lowestSeller?: ProductRef;
  productsWithNoSales: number;
  productsSold: number;
  activeProducts: number;
  inventory: InventoryValue;
  purchases: { purchases: number; unitsReceived: number; totalSpent: number; previousTotalSpent?: number };
}

export type Performance = "GROWING" | "STABLE" | "DECLINING" | "NEW" | "NO_DATA";
export type ProductLabel = "TOP_SELLER" | "FAST_MOVING" | "SLOW_MOVING" | "NO_SALES";

export interface ProductPerformance {
  rank: number;
  productId: number;
  name: string;
  sku?: string;
  categoryName?: string;
  unit: string;
  unitsSold: number;
  revenue: number;
  grossProfit?: number;
  marginPercent?: number;
  salesCount: number;
  averageDailySales: number;
  currentStock: number;
  stockStatus: StockStatus;
  previousUnits: number;
  growthPercent?: number;
  performance: Performance;
  labels: ProductLabel[];
  lastSoldAt?: string;
  archived: boolean;
}

export interface TrendPoint {
  start: string;
  units: number;
  revenue: number;
  grossProfit?: number;
  sales: number;
}

export interface Trend {
  period: Period;
  granularity: "day" | "week" | "month";
  points: TrendPoint[];
}

export interface CategoryPerformance {
  categoryId?: number;
  categoryName: string;
  units: number;
  revenue: number;
  grossProfit?: number;
  productsSold: number;
  previousUnits: number;
  growthPercent?: number;
}

export interface Insight {
  kind: string;
  severity: "GOOD" | "INFO" | "WARNING";
  title: string;
  message: string;
  productId?: number;
  action?: "ADD_STOCK" | "VIEW_PRODUCT";
}

// ---------------------------------------------------------------- sales & purchases

export type PaymentStatus = "PAID" | "PARTIALLY_PAID" | "UNPAID";
export type SaleStatus = "COMPLETED" | "CANCELLED";
export type PurchaseStatus = "RECEIVED" | "CANCELLED";

export const PAYMENT_METHODS = ["CASH", "UPI", "CARD", "BANK_TRANSFER", "CREDIT", "OTHER"] as const;

export interface PaymentInput {
  method: string;
  amount: number;
  reference?: string;
}

export interface Payment {
  id: number;
  kind: "SALE_PAYMENT" | "REFUND" | "PURCHASE_PAYMENT";
  amount: number;
  method: string;
  reference?: string;
  paidAt: string;
  recordedBy?: string;
}

export interface SaleSummary {
  id: number;
  invoiceNumber: string;
  soldAt: string;
  customerName?: string;
  customerPhone?: string;
  itemCount: number;
  totalQuantity: number;
  products: string;
  total: number;
  returnedTotal: number;
  amountPaid: number;
  outstanding: number;
  paymentStatus: PaymentStatus;
  status: SaleStatus;
}

export interface SaleItem {
  id: number;
  lineNo: number;
  productId: number;
  productName: string;
  variantId: number;
  variantName?: string;
  sku?: string;
  unitCode: string;
  warehouseName?: string;
  quantity: number;
  returnedQuantity: number;
  unitPrice: number;
  grossAmount: number;
  discount: number;
  netAmount: number;
  taxRate: number;
  taxAmount: number;
  lineTotal: number;
  unitCost?: number;
  costAmount?: number;
  grossProfit?: number;
}

export interface SaleReturn {
  id: number;
  returnNumber: string;
  returnedAt: string;
  reason: string;
  creditAmount: number;
  refundAmount: number;
  refundMethod?: string;
  recordedBy?: string;
  items: { saleItemId: number; productName: string; quantity: number; resalable: boolean; netAmount: number; taxAmount: number }[];
}

export interface Sale {
  id: number;
  invoiceNumber: string;
  status: SaleStatus;
  soldAt: string;
  customerId?: number;
  customerName?: string;
  customerPhone?: string;
  notes?: string;
  soldBy?: string;
  cancelReason?: string;
  cancelledAt?: string;
  cancelledBy?: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  returnedTotal: number;
  amountPaid: number;
  outstanding: number;
  paymentStatus: PaymentStatus;
  netSales?: number;
  costTotal?: number;
  grossProfit?: number;
  items: SaleItem[];
  payments: Payment[];
  returns: SaleReturn[];
  stock?: { productId: number; productName: string; unitCode: string; available: number; status: StockStatus }[];
}

export interface SaleLineRequest {
  variantId: number;
  warehouseId?: number | null;
  quantity: number;
  unitPrice: number;
  discount?: number;
  taxRate?: number | null;
  batchId?: number | null;
  serials?: string[] | null;
}

export interface SaleRequest {
  items: SaleLineRequest[];
  customerId?: number | null;
  customerName?: string;
  customerPhone?: string;
  saveCustomer?: boolean;
  invoiceNumber?: string;
  payment?: PaymentInput | null;
  notes?: string;
  clientRequestId: string;
}

export interface PurchaseSummary {
  id: number;
  purchaseNumber: string;
  referenceNumber?: string;
  purchasedAt: string;
  supplierName?: string;
  itemCount: number;
  totalQuantity: number;
  products: string;
  total: number;
  amountPaid: number;
  outstanding: number;
  paymentStatus: PaymentStatus;
  status: PurchaseStatus;
}

export interface PurchaseItem {
  id: number;
  lineNo: number;
  productId: number;
  productName: string;
  variantId: number;
  variantName?: string;
  unitCode: string;
  warehouseName?: string;
  quantity: number;
  unitCost: number;
  grossAmount: number;
  discount: number;
  netAmount: number;
  taxRate: number;
  taxAmount: number;
  lineTotal: number;
  landedUnitCost?: number;
}

export interface Purchase {
  id: number;
  purchaseNumber: string;
  referenceNumber?: string;
  status: PurchaseStatus;
  purchasedAt: string;
  supplierId?: number;
  supplierName?: string;
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  amountPaid: number;
  outstanding: number;
  paymentStatus: PaymentStatus;
  notes?: string;
  recordedBy?: string;
  cancelReason?: string;
  cancelledAt?: string;
  items: PurchaseItem[];
  payments: Payment[];
}

export interface PurchaseLineRequest {
  variantId: number;
  warehouseId?: number | null;
  quantity: number;
  unitCost: number;
  discount?: number;
  taxRate?: number | null;
  lotNumber?: string;
  expiryDate?: string;
  serials?: string[] | null;
}

export interface PurchaseRequest {
  items: PurchaseLineRequest[];
  supplierId?: number | null;
  supplierName?: string;
  saveSupplier?: boolean;
  referenceNumber?: string;
  payment?: PaymentInput | null;
  notes?: string;
  clientRequestId: string;
}

export interface Party {
  id: number;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  taxId?: string;
  notes?: string;
  archived: boolean;
  createdAt: string;
}

export interface PartyRequest {
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  taxId?: string;
  notes?: string;
}

// ---------------------------------------------------------------- network & trade

export interface BusinessCard {
  id: number;
  name: string;
  businessType?: string;
  city?: string;
  businessCode?: string;
}

export interface PartyDetails extends BusinessCard {
  phone?: string;
  email?: string;
  address?: string;
  state?: string;
  postalCode?: string;
  taxId?: string;
}

export type ConnectionStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "REMOVED";

export interface Connection {
  id: number;
  status: ConnectionStatus;
  direction: "INCOMING" | "OUTGOING";
  business: BusinessCard;
  message?: string;
  createdAt: string;
  respondedAt?: string;
}

export type TradeInvoiceStatus = "DRAFT" | "PENDING_APPROVAL" | "POSTED" | "REJECTED" | "CANCELLED";
export type TradeReturnStatus = "REQUESTED" | "ACCEPTED" | "REJECTED" | "CANCELLED";

export interface TradeInvoiceSummary {
  id: number;
  invoiceNumber: string;
  status: TradeInvoiceStatus;
  role: "SELLER" | "BUYER";
  counterparty: BusinessCard;
  invoiceDate?: string;
  lineCount: number;
  total: number;
  currencyCode: string;
  createdAt: string;
  sentAt?: string;
  respondedAt?: string;
  returnPending: boolean;
}

export interface TradeLine {
  id: number;
  lineNo: number;
  productName: string;
  variantName?: string;
  defaultVariant: boolean;
  sku?: string;
  unitCode: string;
  unitName?: string;
  unitDecimals: number;
  quantity: number;
  unitPrice: number;
  grossAmount: number;
  discount: number;
  netAmount: number;
  taxRate: number;
  taxAmount: number;
  lineTotal: number;
  returnedQuantity?: number;
}

export interface TradeInvoice {
  id: number;
  invoiceNumber: string;
  status: TradeInvoiceStatus;
  role: "SELLER" | "BUYER";
  seller: PartyDetails;
  buyer: PartyDetails;
  invoiceDate?: string;
  notes?: string;
  deliveryNotes?: string;
  warehouseName?: string;
  lines: (TradeLine & { sellerVariantId?: number; buyerVariantId?: number; buyerProductId?: number })[];
  subtotal: number;
  discountTotal: number;
  taxTotal: number;
  total: number;
  currencyCode: string;
  createdAt: string;
  sentAt?: string;
  respondedAt?: string;
  rejectionReason?: string;
  cancelReason?: string;
  saleId?: number;
  purchaseId?: number;
  returns: {
    id: number;
    returnNumber: string;
    status: TradeReturnStatus;
    reason: string;
    total: number;
    requestedAt: string;
    respondedAt?: string;
    responseNote?: string;
    lines: { invoiceLineId: number; productName: string; variantName?: string; unitCode: string; quantity: number; amount: number }[];
  }[];
  events: { action: string; businessId: number; businessName: string; actorName?: string; note?: string; at: string }[];
}

export interface TradeInvoiceRequest {
  buyerBusinessId: number;
  warehouseId?: number | null;
  notes?: string;
  deliveryNotes?: string;
  lines: { variantId: number; quantity: number; unitPrice?: number | null; discount?: number; taxRate?: number | null }[];
  send: boolean;
  clientRequestId?: string;
}

export interface Candidate {
  variantId: number;
  productId: number;
  productName: string;
  variantName?: string;
  unitCode: string;
  available: number;
}

export interface ReceivePreview {
  invoiceId: number;
  warehouseId: number;
  warehouseName: string;
  lines: { lineId: number; mode: "MAPPED" | "MATCHED" | "NEW" | "REVIEW"; target?: Candidate; candidates?: Candidate[] }[];
}

export interface TradeSummary {
  pendingApprovals: number;
  awaitingBuyer: number;
  drafts: number;
  incomingConnections: number;
  returnsToReview: number;
  connectedBusinesses: number;
}

// ---------------------------------------------------------------- parity additions

export interface UnitConversion {
  id: number;
  fromUnit: Unit;
  toUnit: Unit;
  factor: number;
  productId?: number;
  productName?: string;
}

export interface FormSchema {
  categoryId?: number;
  fields: Attribute[];
  trackBatches: boolean;
  trackExpiry: boolean;
  trackSerials: boolean;
  trackDamagedReserved: boolean;
}

export interface AttributeRequest {
  key: string;
  label: string;
  fieldType: FieldType;
  required: boolean;
  options?: string[];
  sortOrder?: number;
  categoryId?: number | null;
}

export interface VariantRequest {
  name: string;
  sku?: string;
  barcode?: string;
  attributes?: Record<string, unknown>;
  minStock?: number | null;
}

export interface Batch {
  id: number;
  lotNumber?: string;
  expiryDate?: string;
  onHand: number;
}

export interface Serial {
  id: number;
  serial: string;
  status: "IN_STOCK" | "OUT" | "DAMAGED" | "RESERVED";
  warehouseId?: number;
  warehouseName?: string;
  updatedAt?: string;
}

export interface WarehouseRequest {
  name: string;
  code: string;
  address?: string;
  branch: boolean;
}

export interface Location {
  id: number;
  warehouseId: number;
  code: string;
  description?: string;
  archived: boolean;
}

export type CountStatus = "OPEN" | "RECONCILED" | "CANCELLED";

export interface StockCountSummary {
  id: number;
  warehouseId: number;
  warehouseName: string;
  status: CountStatus;
  notes?: string;
  createdBy?: string;
  createdAt: string;
  reconciledAt?: string;
  lineCount: number;
  countedLines: number;
}

export interface CountLine {
  id: number;
  variantId: number;
  productId: number;
  productName: string;
  variantName?: string;
  sku?: string;
  unitCode: string;
  batchId?: number;
  lotNumber?: string;
  expectedQty: number;
  countedQty?: number;
  difference?: number;
}

export interface StockCount {
  summary: StockCountSummary;
  lines: CountLine[];
}

export interface SaleReturnRequest {
  items: { saleItemId: number; quantity: number; resalable: boolean }[];
  reason: string;
  refundAmount?: number | null;
  refundMethod?: string;
  notes?: string;
  clientRequestId: string;
}

export interface SearchResult {
  business: BusinessCard;
  connectionId?: number;
  connectionStatus?: ConnectionStatus;
  direction?: "INCOMING" | "OUTGOING";
}

export interface ProductMapping {
  id: number;
  seller: BusinessCard;
  sellerVariantId: number;
  sellerProductName: string;
  buyerVariantId: number;
  buyerProductId: number;
  buyerProductName: string;
  buyerVariantName?: string;
  updatedAt: string;
}
