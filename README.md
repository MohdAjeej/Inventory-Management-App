# Inventory Platform

A multi-tenant inventory management system that any kind of business can configure: a pharmacy, a bookstore, a hardware shop, a bakery, an electronics distributor, and so on. Each business defines its own categories, custom product fields, units, warehouses and team. Besides inventory it records sales, purchases, payments and profit, and lets connected businesses invoice each other. It is not a full accounting system.

This README is for developers. Customer-facing documentation is in [`CUSTOMER_PROJECT_DOCUMENTATION.md`](CUSTOMER_PROJECT_DOCUMENTATION.md).

| Part | Stack |
|------|-------|
| Android app | Kotlin, Jetpack Compose (Material 3), ViewModel + StateFlow, Retrofit + OkHttp, kotlinx.serialization, DataStore |
| Backend | Java 21, Spring Boot 3.4, Spring Security with JWT, Spring Data JPA, Flyway, springdoc-openapi, Apache POI |
| Database | PostgreSQL 16 (Docker Compose) |
| Build | Gradle (wrappers included) |

---

## Features

- **Workspaces for any business**: registration creates the business, its first administrator, neutral default units (pieces, boxes, kg, litres, metres…) and a *Main Warehouse*. The business type is a description only and never restricts features.
- **Tenant isolation**: the business is always derived from the authenticated membership, never from request parameters. Every lookup is scoped to that business, so another business's ids return `404`. Integration tests check this.
- **Dynamic catalog**:
  - Category and subcategory tree with archiving.
  - Custom fields per category or for all products: text, number, date, yes/no, dropdown and multi-select, each optionally required. Subcategories inherit their parents' fields.
  - Fields are stored as PostgreSQL `JSONB` and validated **on the server**.
  - Units with decimal precision, and unit conversions (general or product-specific).
- **Products**:
  - Simple form (name, category, quantity, unit), plus the category's fields and optional brand, model, SKU, barcode, description, images and minimum stock.
  - Variants (size, colour…), each with its own SKU, barcode and threshold.
  - Search by name, brand, SKU or barcode. Filter by category (including subcategories), stock status and archive state.
- **Stock operations**:
  - Opening stock, add, reduce, adjust (with a required reason), transfer between warehouses, damaged and reserved stock, optional batch/lot with expiry, and serial numbers.
  - Each operation locks the stock-balance row (`SELECT … FOR UPDATE`) inside a transaction.
  - Stock can't go negative unless the business allows it.
- **Audit**: every change appends an immutable `stock_movements` row recording type, quantity, before and after quantities, reason, notes, who did it and when. A database trigger blocks `UPDATE` and `DELETE` on that table. Configuration changes go to `audit_events`.
- **Stock counts**: snapshot a warehouse (optionally one category), enter counted quantities, then reconcile into `COUNT_ADJUST` movements.
- **Warehouses and branches**: a default warehouse, storage locations, archiving (only when empty).
- **Employees**:
  - Three roles: Business Admin, Inventory Manager and Staff.
  - Per-person permission overrides.
  - Invitations by shareable code, plus deactivation, which signs the person out immediately.
  - Every permission is enforced on the server.
- **Dashboard**: total products, total stock, low and out-of-stock items, quick actions, recently updated products, recent stock activity, today's sales (for members who can view analytics), and summaries by category and by warehouse.
- **Pricing**: purchase price, selling price and default tax rate per product. Cost uses the weighted average across purchases. The product page shows profit per unit, margin, stock value and potential profit.
- **Sales**:
  - Multi-product sales with discounts, tax, an optional customer, an invoice number, a date and time, and payments (Cash, UPI, Card, Bank transfer, Credit).
  - Payment status can be Paid, Partly paid or Unpaid, and more payments can be added later.
  - Stock is reduced automatically in the same transaction.
  - Price and cost are saved with every line, so later price changes don't rewrite history.
  - Each sale has a PDF receipt that can be shared or printed.
- **Returns and cancellations**:
  - Partial returns: resalable items are restocked and damaged items are recorded separately, with refunds.
  - Full cancellation restores stock exactly once.
  - Nothing is overwritten.
- **Purchases and suppliers**: record goods received from dealers. This adds stock, updates the average cost, tracks payments to suppliers, and can be cancelled (which removes the stock again).
- **Sales analytics**:
  - Today's and this month's sales, net sales, cost of goods sold, gross profit and margin, returns and discounts.
  - Best, lowest and never-sold products, fast and slow movers, and trending and declining products.
  - Profit by product and over time, sales by category, stock value and potential profit, and plain-language insights.
  - Date presets and custom ranges, compared with the previous period. The product ranking exports to CSV or Excel.
- **Reports**: inventory, low stock and movements, with date, warehouse, category and type filters, plus **CSV and Excel (.xlsx) export**.
- **Notifications**:
  - In-app low-stock, out-of-stock and team alerts, deduplicated so repeated movements don't spam.
  - Delivery goes through a `NotificationChannel` interface, so a push channel (e.g. FCM) can be added as another bean.

### Roles (defaults; admins can override per person)

| Permission | Admin | Inventory Manager | Staff |
|---|:-:|:-:|:-:|
| View products and stock | ✓ | ✓ | ✓ |
| Create / edit / archive products | ✓ | ✓ | |
| Add stock / reduce stock | ✓ | ✓ | ✓ |
| Adjust stock, damaged and reserved | ✓ | ✓ | |
| Transfer stock | ✓ | ✓ | |
| Run stock counts | ✓ | ✓ | ✓ (reconcile needs *Adjust*) |
| View and export reports | ✓ | ✓ | |
| Record sales, take payments, manage customers | ✓ | ✓ | ✓ |
| Cancel sales / record returns | ✓ | ✓ | |
| Record purchases, manage suppliers | ✓ | ✓ | |
| View sales, profit and analytics (and see purchase costs) | ✓ | ✓ | |
| Manage categories, fields and units | ✓ | ✓ | |
| Manage warehouses | ✓ | ✓ | |
| Manage employees | ✓ | | |
| Business profile and settings | ✓ | | |

---

## Prerequisites (Windows)

- **JDK 21**, used by the backend (`backend/gradle.properties` points Gradle at it).
- **JDK 17 or newer** for the Android build (`android/gradle.properties` points at `C:\Program Files\Java\jdk-17`; change it if yours is elsewhere).
- **Android SDK** with platform 35. `android/local.properties` must contain `sdk.dir=...` (Android Studio creates it).
- **Docker Desktop**, which must be running for PostgreSQL and for the backend tests (Testcontainers).
- An Android phone with USB debugging enabled, or an emulator.

## Configuration

Copy the sample environment file and set your own secrets:

```powershell
Copy-Item .env.example .env
# Edit .env: set POSTGRES_PASSWORD / DB_PASSWORD, and generate a JWT secret:
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

`.env` is git-ignored. The backend reads it automatically (`spring.config.import`), and so does Docker Compose. Secrets are not stored in source code.

| Variable | Purpose | Default |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database container | `inventory_platform` / `postgres` / – |
| `POSTGRES_PORT` | Host port for the database | `5434` (avoids a local PostgreSQL on 5432) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Backend datasource | `jdbc:postgresql://localhost:5434/inventory_platform` |
| `JWT_SECRET` | HMAC key, base64, ≥ 32 bytes | **required** |
| `SERVER_PORT` | Backend port | `8082` |
| `UPLOADS_DIR` | Where product images and logos are stored | `uploads` |

## Run it (exact PowerShell commands)

From the repository root:

```powershell
# 1. Start PostgreSQL (Docker Desktop must be running)
docker compose up -d postgres
docker compose ps        # wait until inventory-platform-db is "healthy"

# 2. Run the backend (Flyway creates the schema on first start)
cd backend
.\gradlew.bat bootRun
#    API:     http://localhost:8082
#    Swagger: http://localhost:8082/swagger-ui.html
```

In a second PowerShell window, build and install the app:

```powershell
cd android

# Physical phone on the same Wi-Fi: use your PC's LAN address (find it with: ipconfig)
.\gradlew.bat assembleDebug -PapiBaseUrl=http://192.168.29.166:8082/
#   (emulator instead: -PapiBaseUrl=http://10.0.2.2:8082/)

adb devices                                              # phone must be listed
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.b2binventory.app/.MainActivity
```

If you leave out `-PapiBaseUrl`, the default is `http://192.168.29.166:8082/`. You can also set it permanently in `%USERPROFILE%\.gradle\gradle.properties` as `apiBaseUrl=http://<your-ip>:8082/`.

**Phone can't reach the backend?** Allow port 8082 through Windows Firewall (run PowerShell as Administrator), then open `http://<pc-ip>:8082/v3/api-docs` in the phone's browser to check:

```powershell
New-NetFirewallRule -DisplayName "Inventory backend 8082" -Direction Inbound -Protocol TCP -LocalPort 8082 -Action Allow
```

Plain HTTP is allowed only in **debug** builds (`app/src/debug/res/xml/network_security_config.xml`). Release builds need HTTPS.

### Everything in Docker (optional)

```powershell
docker compose --profile full up -d --build    # database + backend container on :8082
```

## Tests

```powershell
cd backend
.\gradlew.bat test
```

The suite starts a throwaway PostgreSQL container and covers:
- registration, login, token rotation and validation errors;
- role and permission enforcement, overrides, and deactivation taking effect immediately;
- tenant isolation across products, categories, warehouses, stock, reports and lookups;
- stock rules: negative stock, decimals, transfers, damaged and reserved stock, batches and expiry, serial numbers;
- **20 concurrent reductions never overselling**;
- stock-count reconciliation, low-stock notifications, and CSV/XLSX exports.

## Using the app (first run)

1. **Register a new business**: business details, then your administrator account.
2. Optionally go to **More → Business settings** to turn on batch/expiry, serial numbers, or damaged/reserved tracking, and set the default low-stock threshold.
3. **More → Categories & fields**: create categories and subcategories, then add custom fields (for example *Author* for books, or *Strength* and *Dosage form* for medicines).
4. **Products → Add product**: name, category, unit and opening quantity. The category's fields appear automatically.
5. **Stock → Update stock** (or the buttons on a product) to add, reduce, adjust or transfer. Every change appears in the history.
6. **More → Employees → Invite**: share the code. The person taps *I have an invitation code* on the sign-in screen.
7. **Reports**: filter, then export CSV or Excel. Files are saved to Downloads, and you're offered a share sheet.

## Business network and business invoices

Businesses on the platform can trade with each other. For example, a wholesaler (Deepak Traders) invoices a retailer (Shri Ji Sales). The retailer approves the invoice, and both inventories update automatically, so the retailer never re-types anything.

**Workflow (More → Business network):**

1. **Connect.** Search for a business by name, by its business code (shown on the Network screen, e.g. `B7K3F9QA`), or by exact phone number. Then send a request; the other business accepts it.
   - Search shows only the name, type, city and code.
   - A business can hide itself from search.
   - Connecting needs the *Business settings* permission.
2. **Invoice (seller).** In *Invoices to businesses → New invoice*, pick a connected buyer, add products (exact size/variant), quantities, prices, discounts and tax. Then send it, or save it as a draft.
   - All amounts are calculated on the server.
   - Sending **reserves** the seller's stock: on hand is unchanged, but the stock can't be sold elsewhere.
   - The buyer gets an in-app notification, and the dashboard shows "N purchase invoices need your approval". The card refreshes every minute.
3. **Approve or reject (buyer).** *Review and approve* shows where each line will go in the buyer's stock:
   - **Product matching:** a saved product link is used first, then the same barcode, then the same name, variant and unit (only when exactly one product matches). If several products match, the buyer must choose one. If none matches, a new product is created with the same name, size, specifications, unit and price.
   - **Units:** units are never converted. A line invoiced in sheets can only go into a product counted in sheets.
4. **Posting.** Approval runs in **one database transaction**:
   - The seller's reservation is released and an ordinary **sale** is recorded for the seller, with the same invoice number and the buyer as customer. This deducts the stock.
   - An ordinary **purchase** is recorded for the buyer at the agreed prices, with the seller as supplier. This adds the stock and updates the average cost.
   - If anything fails, nothing is saved and the invoice stays pending. Approving twice changes nothing.
5. **Reject or cancel.** The buyer can reject with a reason; the seller can cancel a pending invoice. Either way the reservation is released and no stock moves.
6. **Returns.** On an approved invoice the buyer can request a return, which holds the buyer's stock. When the seller accepts:
   - The goods leave the buyer's stock as a *Returned to supplier* movement.
   - They come back to the seller as a return on the original sale.

**Statuses:**
- Invoices: `DRAFT` → `PENDING_APPROVAL` → `POSTED`, or `REJECTED` / `CANCELLED`.
- Returns: `REQUESTED` → `ACCEPTED`, or `REJECTED` / `CANCELLED`.

**Privacy and permissions:**
- Each request is checked on the server: the caller's business must be the seller or the buyer, otherwise the server answers 404.
- Drafts are visible only to the seller.
- Each side sees the other's contact details only on shared invoices, and staff names only from its own business.
- Seller actions need *Record sales*. Buyer actions need *Manage purchases*. Accepting returns needs *Cancel sales*.

**Endpoints:**
- `/api/network/*`: search, connections, your business code.
- `/api/trade/*`: invoices, preview, approve, reject, cancel, returns, product links, summary.

**Database:** migration `V4__business_network.sql` runs automatically through Flyway when the backend starts. It only adds new tables and columns. Existing businesses get a business code.

**Limitations:**
- Products tracked by serial number or batch can't be put on business invoices yet.
- Payments are recorded separately by each business (in Sales / Purchases).
- Invoices don't expire; the seller cancels them.
- Unit conversion is not applied between businesses.
- Backups contain each business's own sales and purchases, but not the shared invoice records or connections.

## API

See [`docs/API.md`](docs/API.md) for an overview. Interactive documentation is at `/swagger-ui.html`: authorise with the `accessToken` from `POST /api/auth/login`.

## Project layout

```
backend/src/main/java/com/b2binventory/
  config/        security, OpenAPI, properties
  controller/    REST endpoints (no business id parameters)
  dto/           request/response records with validation
  entity/        JPA entities
  exception/     error types + global JSON error handler
  repository/    Spring Data repositories (tenant-scoped finders)
  security/      JWT, current user, permissions
  service/       business logic, stock engine, reports, notifications
  validation/    custom-field validation
backend/src/main/resources/db/migration/   Flyway migrations
android/app/src/main/java/com/b2binventory/app/
  data/          Retrofit API, DTOs, session store, auth repository
  di/            manual dependency container
  ui/            Compose screens + ViewModels per feature
legacy/          the previous version of the code, kept for reference (not built)
```

## Notes and limitations

- Invitations are shared as codes (there's a share button); no email server is configured.
- Notifications are in-app; push delivery needs a `NotificationChannel` implementation (for example FCM) and device token registration.
- Storage locations are managed per warehouse, but stock balances are tracked per warehouse, not per bin.
- Images are stored on the backend's local disk (`UPLOADS_DIR`) under random names and served from `/api/files/…`. Use object storage in production.
- The schema is created by Flyway (`ddl-auto: validate`). This version starts from a fresh `inventory_platform` database; data in the previous `b2b_inventory` database is not migrated.
