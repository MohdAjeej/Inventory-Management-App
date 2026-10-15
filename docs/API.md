# API overview

Base URL: `http://<host>:8082`. Interactive docs: `/swagger-ui.html`; OpenAPI JSON: `/v3/api-docs`.

## Conventions

- **Authentication**: `Authorization: Bearer <accessToken>`. Access tokens last 60 minutes. Exchange the refresh token at `POST /api/auth/refresh`; refresh tokens rotate on each use, and replaying a used one revokes the session.
- **Tenancy**: the business comes from the token. No endpoint accepts a business id, and records of other businesses return `404`.
- **Permissions**: endpoints return `403` when the member lacks the permission (see the README for role defaults).
- **Errors** always use this shape:
  ```json
  { "status": 400, "code": "VALIDATION_FAILED", "message": "Validation failed",
    "fieldErrors": { "name": "Product name is required", "attributes.fabric": "Fabric is required" },
    "timestamp": "2026-10-14T10:00:00Z" }
  ```
  Codes: `VALIDATION_FAILED`, `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `CONCURRENT_MODIFICATION`, `INSUFFICIENT_STOCK` (422), `INTERNAL_ERROR`.
- **Pagination**: `?page=0&size=20`. Responses look like `{content, page, size, totalElements, totalPages}`.
- **Quantities** are decimals. Each unit defines how many decimal places it allows.

## Auth (`/api/auth`)

| Method | Path | Notes |
|---|---|---|
| POST | `/register` | New business + admin. Body: businessName, businessType?, businessEmail?, businessPhone?, address?, city?, state?, country?, fullName, email, phone?, password |
| POST | `/login` | `{email, password, businessId?}` → `{accessToken, refreshToken, expiresIn, user}` |
| POST | `/refresh` | `{refreshToken}` |
| POST | `/logout` | `{refreshToken}` |
| GET | `/invitations/{code}` | Preview an invitation (public) |
| POST | `/accept-invitation` | `{code, fullName?, phone?, password}` |
| POST | `/switch-business` | `{businessId}`. The caller must be an active member of it |
| GET/PUT | `/me` | Current member. PUT body: `{fullName, phone?}` |
| POST | `/change-password` | `{currentPassword, newPassword}`. Revokes all sessions |

## Business and team

| Method | Path | Permission |
|---|---|---|
| GET / PUT | `/api/business` | PUT: BUSINESS_SETTINGS |
| POST | `/api/business/logo` (multipart `file`) | BUSINESS_SETTINGS |
| GET / PUT | `/api/business/settings` | PUT: BUSINESS_SETTINGS |
| GET | `/api/permissions` | Permission catalog and role defaults |
| GET | `/api/employees?q=` , `/api/employees/{id}` | EMPLOYEE_MANAGE |
| PUT | `/api/employees/{id}/role` `{role}` | EMPLOYEE_MANAGE |
| PUT | `/api/employees/{id}/permissions` `{overrides: {PERMISSION: true/false}}` | EMPLOYEE_MANAGE |
| POST | `/api/employees/{id}/deactivate` · `/reactivate` | EMPLOYEE_MANAGE |
| GET / POST | `/api/invitations` `{email, role}` · DELETE `/api/invitations/{id}` | EMPLOYEE_MANAGE |

## Catalog configuration

| Method | Path | Permission for writes |
|---|---|---|
| GET/POST | `/api/units` · PUT `/api/units/{id}` · POST `/{id}/archive` · `/{id}/restore` | CATEGORY_MANAGE |
| GET/POST | `/api/unit-conversions` · DELETE `/{id}` · GET `/convert?quantity&fromUnitId&toUnitId&productId?` | CATEGORY_MANAGE |
| GET/POST | `/api/categories?includeArchived` (tree) · GET/PUT `/{id}` · POST `/{id}/archive` · `/{id}/restore` | CATEGORY_MANAGE |
| GET/POST | `/api/attributes?categoryId` · PUT `/{id}` · POST `/{id}/archive` · `/{id}/restore` | CATEGORY_MANAGE |
| GET | `/api/form-schema?categoryId=` | Fields for a product in that category: business-wide fields plus the category's and its ancestors' |

Attribute definition: `{key, label, fieldType: TEXT|NUMBER|DATE|BOOLEAN|DROPDOWN|MULTI_SELECT, required, options[], sortOrder, categoryId?}`.

## Products

| Method | Path | Permission |
|---|---|---|
| GET | `/api/products?q&categoryId&warehouseId&status=IN_STOCK|LOW_STOCK|OUT_OF_STOCK&archived&sort=name|updated|created` | PRODUCT_VIEW |
| GET | `/api/products/lookup?code=` (exact SKU or barcode) · `/api/products/{id}` | PRODUCT_VIEW |
| POST | `/api/products` | PRODUCT_CREATE (+ STOCK_ADD when sending opening stock) |
| PUT | `/api/products/{id}` | PRODUCT_EDIT |
| POST | `/api/products/{id}/archive` · `/restore` | PRODUCT_ARCHIVE |
| POST | `/api/products/{id}/variants` · PUT `/api/variants/{id}` | PRODUCT_EDIT |
| POST | `/api/variants/{id}/archive` · `/restore` | PRODUCT_ARCHIVE |
| POST | `/api/products/{id}/images` (multipart `file`; JPEG/PNG/WebP ≤ 5 MB) · DELETE `/images/{imageId}` | PRODUCT_EDIT |

Product request:
```json
{ "name": "Paracetamol 500mg", "categoryId": 3, "baseUnitId": 1, "brand": "Acme", "sku": "PCM-500",
  "barcode": "8901234567890", "minStock": 20, "attributes": { "strength": "500 mg", "form": "Tablet" },
  "trackBatches": true,
  "openingStock": { "quantity": 100, "warehouseId": 1, "lotNumber": "L2401", "expiryDate": "2027-06-30" } }
```

## Stock

| Method | Path | Permission |
|---|---|---|
| POST | `/api/stock/operations` | ADD → STOCK_ADD; REDUCE → STOCK_REDUCE; ADJUST, DAMAGE, RESERVE, RELEASE → STOCK_ADJUST |
| POST | `/api/stock/transfers` | STOCK_TRANSFER |
| GET | `/api/stock/movements?productId&variantId&warehouseId&types&actorId&from&to` (ISO instants) | PRODUCT_VIEW |
| GET | `/api/variants/{id}/balances` · `/batches` · `/serials` | PRODUCT_VIEW |
| GET/POST | `/api/stock-counts` · GET `/{id}` · PUT `/{id}/lines` · POST `/{id}/cancel` | STOCK_COUNT |
| POST | `/api/stock-counts/{id}/reconcile` | STOCK_COUNT + STOCK_ADJUST |

Operation request:
```json
{ "type": "REDUCE", "variantId": 12, "warehouseId": 1, "quantity": 3, "reason": "Issued",
  "notes": "Ward 4", "batchId": 7, "serials": null, "fromDamaged": false }
```
For `ADJUST`, `quantity` is the corrected on-hand quantity and `reason` is required. The response holds the created movements and the variant's balances.

Transfer request: `{variantId, fromWarehouseId, toWarehouseId, quantity, batchId?, serials?, reason?, notes?}`. It writes paired `TRANSFER_OUT` and `TRANSFER_IN` movements with the same `transferGroupId`.

## Warehouses

`GET/POST /api/warehouses`, `GET/PUT /api/warehouses/{id}`, `POST /{id}/archive|restore|default`, `GET/POST /api/warehouses/{id}/locations`, `PUT /api/locations/{id}`, `POST /api/locations/{id}/archive|restore`. Writes require WAREHOUSE_MANAGE.

## Dashboard, reports and notifications

| Method | Path | Permission |
|---|---|---|
| GET | `/api/dashboard` (includes `sales`: today's and this month's sales and profit, best and lowest seller, stock value; only for ANALYTICS_VIEW) | PRODUCT_VIEW |
| GET | `/api/reports/inventory?warehouseId&categoryId&q&status` | REPORT_VIEW |
| GET | `/api/reports/low-stock?warehouseId&categoryId` | REPORT_VIEW |
| GET | `/api/reports/movements?warehouseId&productId&types&actorId&from&to` (ISO dates) | REPORT_VIEW |
| GET | `/api/reports/{inventory|low-stock|movements}/export?format=csv|xlsx&…same filters` | REPORT_EXPORT |
| GET | `/api/notifications?unreadOnly` · `/unread-count` · POST `/{id}/read` · `/read-all` | signed in |

## Products: prices

`ProductRequest` also takes `purchasePrice`, `salePrice`, `taxRate` (percent) and `supplierId`. The product response adds:
- `averageCost`: the weighted-average cost of stock on hand.
- `pricing`: `unitCost`, `costSource` (`AVERAGE` or `PURCHASE_PRICE`), `profitPerUnit`, `marginPercent`, stock value at cost and at selling price, and `potentialProfit` on available stock.

Purchase prices, average cost and `pricing` are only returned to members with ANALYTICS_VIEW, PURCHASE_MANAGE or PRODUCT_EDIT.

## Sales

| Method | Path | Permission |
|---|---|---|
| POST | `/api/sales` | SALE_RECORD |
| GET | `/api/sales?q&productId&customerId&paymentStatus&status&from&to` (`q` = invoice no., sale id, customer name or phone) | SALE_RECORD, SALE_CANCEL or ANALYTICS_VIEW |
| GET | `/api/sales/{id}` | same |
| POST | `/api/sales/{id}/payments` `{amount, method, reference?, clientRequestId}` | SALE_RECORD |
| POST | `/api/sales/{id}/cancel` `{reason, refundMethod?}` | SALE_CANCEL |
| POST | `/api/sales/{id}/returns` | SALE_CANCEL |

Sale request:
```json
{ "items": [ { "variantId": 12, "quantity": 7, "unitPrice": 120, "discount": 0, "taxRate": 18, "batchId": null, "serials": null } ],
  "warehouseId": null, "customerId": null, "customerName": "Ravi", "customerPhone": "98…", "saveCustomer": true,
  "invoiceNumber": null, "soldAt": "2026-10-14T09:30:00Z",
  "payment": { "method": "UPI", "amount": 500, "reference": "…" }, "notes": null, "clientRequestId": "9f1c…" }
```

**Rules**
- **Line amounts.** Prices exclude tax.
  - Line net = quantity × price − discount (the discount can't exceed the line amount).
  - Tax = net × rate.
  - Line total = net + tax.
  - A line without `taxRate` uses the product's default.
- **One transaction.** The whole sale is saved in a single transaction: header, lines, a `SALE` stock movement per line, cost snapshots and the first payment. If one line fails (for example `INSUFFICIENT_STOCK`, 422), nothing is saved. Balances are row-locked, so concurrent sales cannot oversell.
- **Price and cost snapshots.** Each line stores the selling price and the unit cost at the time of sale. Cost is the weighted-average cost, falling back to the purchase price. Changing prices later never changes past sales.
- **Payments.** `paymentStatus` is `PAID`, `PARTIALLY_PAID` or `UNPAID`, and `outstanding` = total − returned value − net paid. A sale is not assumed to be paid: send `payment` or add payments later.
- **Cancel.** Cancelling puts back all unreturned stock (`SALE_CANCEL` movements), refunds the money received, and marks the sale `CANCELLED`. The record and its audit trail are kept. A second cancel returns 409.
- **Returns.**
  - Request: `{items:[{saleItemId, quantity, resalable, serials?}], reason, refundAmount?, refundMethod?, clientRequestId}`.
  - You cannot return more than was sold minus earlier returns.
  - Resalable items are restocked (`RETURN_IN`). Damaged items are recorded but not restocked, and their cost stays in cost of goods sold.
  - The refund is limited to the value returned and the money received.
- **Idempotency.** `clientRequestId` makes sales, payments, returns, purchases and stock operations safe to retry: the same id returns the original result.

## Purchases (PURCHASE_MANAGE)

`POST /api/purchases`, `GET /api/purchases?q&supplierId&paymentStatus&status&from&to`, `GET /api/purchases/{id}`, `POST /{id}/payments`, `POST /{id}/cancel {reason}`.

Purchase request:
```json
{ "items": [ { "variantId": 12, "quantity": 50, "unitCost": 110, "discount": 0, "taxRate": 0,
              "lotNumber": null, "expiryDate": null, "serials": null } ],
  "supplierId": null, "supplierName": "Acme Traders", "saveSupplier": true, "referenceNumber": "BILL-77",
  "purchasedAt": null, "payment": { "method": "BANK_TRANSFER", "amount": 2000 }, "clientRequestId": "…" }
```

**Recording a purchase**
- Adds stock through `PURCHASE` movements.
- Updates the product's weighted-average cost: (stock before × old average + received × net unit cost) ÷ stock after.
- Sets the default purchase price to the latest cost.

**Cancelling a purchase**
- Removes the stock again. This fails with 422 if some of it has already been sold.
- Reverses the cost effect.

## Customers and suppliers

- **Customers:** `GET/POST /api/customers`, `PUT /api/customers/{id}`, `POST /{id}/archive|restore`. Managing them needs SALE_RECORD; reading them needs SALE_RECORD, SALE_CANCEL or ANALYTICS_VIEW.
- **Suppliers:** `GET/POST /api/suppliers`, `PUT /api/suppliers/{id}`, `POST /{id}/archive|restore`. Managing them needs PURCHASE_MANAGE; reading them needs PURCHASE_MANAGE or product edit/create.

## Analytics (ANALYTICS_VIEW)

`from`/`to` are inclusive ISO dates in the business timezone (`/api/business/settings` → `timezone`). The default is the last 30 days. Each period is compared with the previous period of the same length.

- **What counts:** only COMPLETED sales count. Returns are subtracted from the period of the original sale. Ordinary stock reductions are never sales.
- **Revenue** means net sales: gross sales − discounts − returns, excluding tax.
- **Gross profit** = net sales − cost of goods sold, using the cost saved on each sale line. It is null ("unavailable") when any line in scope has no cost.
- **The overview** also includes:
  - `financials`: gross sales, discounts, returns, net sales, tax collected, cost of goods sold, gross profit, margin
  - `inventory`: stock value at cost and at selling price, and potential profit
  - `purchases`: purchases in the period
- **Product rows** include `grossProfit` and `marginPercent`, and `sort=profit` is supported.

| Method | Path | Returns |
|---|---|---|
| GET | `/api/analytics/overview?from&to` | Sales count, units and revenue (each with `previous` and `growthPercent`), daily averages, best and lowest seller, products with no sales |
| GET | `/api/analytics/products?from&to&categoryId&q&sort=units|revenue|growth|name` | Ranked products: units, revenue, current stock, average daily sales, stock status, performance (`GROWING`, `STABLE`, `DECLINING`, `NEW`, `NO_DATA`) and labels (`TOP_SELLER`, `FAST_MOVING`, `SLOW_MOVING`, `NO_SALES`) |
| GET | `/api/analytics/trend?from&to&granularity=day|week|month` | Zero-filled time series |
| GET | `/api/analytics/categories?from&to` | Units and revenue by category, with growth |
| GET | `/api/analytics/insights?from&to` | Plain-language findings with an optional `productId` and `action` (`ADD_STOCK`, `VIEW_PRODUCT`) |
| GET | `/api/analytics/products/export?format=csv|xlsx&…` | Product ranking file (also needs REPORT_EXPORT) |

A missing `growthPercent` means the previous period has nothing to compare with ("Insufficient data").
