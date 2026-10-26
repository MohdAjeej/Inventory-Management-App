# Inventory Platform — website and web dashboard

Public website and web dashboard for the Inventory Platform, built with Next.js (App Router), TypeScript and Tailwind CSS. It talks to the existing Spring Boot backend in `../backend` and leaves the backend and the Android app unchanged.

| Part | What it is |
|---|---|
| Public site | `/`, `/features`, `/solutions`, `/about`, `/contact` |
| Auth | `/login`, `/signup` (new business + admin), `/join` (invitation code) |
| Live dashboard | `/dashboard/...`. Needs a signed-in session and the backend running. Same features as the Android app (see below). |
| Demo dashboard | `/dashboard/demo/...`. The same screens with in-memory sample data, clearly labelled. Nothing is saved. |

## Quick start

```powershell
cd website
npm install
Copy-Item .env.example .env.local   # then set API_BASE_URL
npm run dev                         # http://localhost:3000
```

To use the live dashboard, start PostgreSQL and the backend first (see `../README.md`):

```powershell
docker compose up -d postgres
cd backend; .\gradlew.bat bootRun   # API on http://localhost:8082
```

The demo dashboard (`/dashboard/demo`) works without a backend.

### Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Development server |
| `npm run build` / `npm start` | Production build and server |
| `npm run lint` | ESLint |
| `npm run typecheck` | `tsc --noEmit` |

## Environment variables

| Variable | Required | Purpose |
|---|---|---|
| `API_BASE_URL` | yes (live mode) | Backend URL, e.g. `http://localhost:8082`. **Server-side only.** |
| `SESSION_COOKIE_SECURE` | no | `true`/`false`. Defaults to `true` in production builds. |
| `NEXT_PUBLIC_SITE_URL` | no | Public site URL for metadata. |
| `CONTACT_WEBHOOK_URL` | no | Receives contact-form submissions as JSON. Without it, the form says plainly that delivery isn't configured. |

No secrets are stored in the code. `.env*` files are git-ignored, except `.env.example`.

## Architecture

```
browser ──► Next.js (same origin)
              ├─ /api/auth/{login,register,accept-invitation,switch-business,logout}
              │     calls the backend and stores tokens in httpOnly cookies
              ├─ /api/backend/<path>  ──►  ${API_BASE_URL}/api/<path>
              │     adds Authorization: Bearer <access token>; refreshes once on 401
              └─ /api/contact          validates the form and forwards it to an optional webhook
```

**Why a proxy?**
- The backend enables CORS without configuring it (`SecurityConfig.java`), so a browser on another origin is blocked.
- A same-origin proxy avoids changing the backend.
- Tokens never reach browser JavaScript.

**Token refresh**
- Refresh tokens rotate on every use, and replaying an old one revokes the session.
- The proxy shares one refresh call between concurrent requests (`lib/server/session.ts`).
- That guard works per server process. Multi-instance deployments need sticky sessions or a shared store.

**Tenancy:** the web app never sends a business id. The backend derives the business from the token. Switching business issues new tokens, then fully reloads the page so no data from the previous business stays in memory.

**Permissions:** navigation, quick actions and buttons are gated on the member's effective `permissions`, as in the Android app. The backend still enforces every rule.

### Folder layout

```
website/
  app/
    (marketing)/          public pages (server components)
    (auth)/               login, signup, join
    dashboard/(live)/     live dashboard: layout (session + shell) + [[...slug]] page
    dashboard/demo/       demo dashboard: same screens, demo data provider
    api/                  auth handlers, backend proxy, contact endpoint
  components/
    ui/                   buttons, fields, cards, chips, dialog, table, tabs, toast, states
    dashboard/            provider, shell, shared parts, stock dialog, screens/*
    marketing/, auth/, charts/
  lib/
    api/                  types.ts (DTO mirror), client.ts (ApiClient), endpoints.ts
    demo/                 DEMO ONLY: in-memory store + request router
    server/               server-only session and cookie helpers
    routes.ts             screen table and navigation shared by live and demo
    format.ts, permissions.ts, validation.ts, site.ts
  middleware.ts           redirects signed-out visitors from /dashboard to /login
```

### Data layer

- Every screen calls typed functions in `lib/api/endpoints.ts`, and each function takes an `ApiClient`.
- `createLiveClient()` calls `/api/backend/*`. `createDemoClient()` serves `lib/demo`.
- To connect a screen to a new endpoint, add one function to `endpoints.ts`, plus a route in `lib/demo/client.ts` if the demo should support it.
- If the live API fails, the screen shows an error state. It never falls back to demo data.

## Screens and API coverage

| Screen | Endpoints used | Web writes |
|---|---|---|
| Overview | `GET /dashboard`, `/trade/summary`, `/analytics/trend` | – |
| Products | `GET /products`, `/products/{id}`, `/stock/movements`, `/categories`, `/units`, `/warehouses`, `/suppliers` | Create (with opening stock), archive/restore, stock operations |
| Categories & units | `GET /categories`, `/attributes`, `/units` | Read-only (managed in the app) |
| Inventory | `GET /reports/inventory`, `/stock/movements`, `/warehouses` | `POST /stock/operations` (add/reduce/adjust/damage), `POST /stock/transfers` |
| Warehouses | `GET /warehouses` | Read-only |
| Sales | `GET/POST /sales`, `/sales/{id}`, `/payments`, `/cancel` | Record sale, add payment, cancel |
| Purchases | `GET/POST /purchases`, `/purchases/{id}`, `/payments`, `/cancel` | Record purchase, add payment, cancel |
| Customers / Suppliers | `GET/POST/PUT /customers`, `/suppliers`, `/archive`, `/restore` | Create, edit, archive |
| B2B invoices | `/trade/invoices` (list/get/create/send/cancel/reject/delete/preview/approve), `/network/connections` | Create and send or save a draft; cancel; review and approve (including `NEEDS_REVIEW`); reject |
| Reports & analytics | `/analytics/overview|products|categories|insights|trend`, `/reports/movement-summary`, exports | CSV/XLSX downloads through the proxy |
| Employees | `/employees`, `/employees/{id}/role|deactivate|reactivate`, `/invitations` | Invite (shows the code), change role, deactivate, cancel invitation |
| Business settings | `/business`, `/business/settings`, `/network/me` | Edit profile and settings (BUSINESS_SETTINGS) |
| Profile | `/auth/me`, `/auth/change-password` | Edit name and phone, change password |
| Notifications | `/notifications`, `/read`, `/read-all` | Mark as read |

### Business rules the UI preserves

- Totals, tax, stock and profit come from the server. Client totals are labelled "estimate".
- Saving a sale deducts stock in one transaction. `INSUFFICIENT_STOCK` (422) is shown inline and nothing is saved.
- Sales, payments, purchases and stock operations send a `clientRequestId` so retries are safe.
- ADJUST sends the corrected on-hand quantity, and a reason is required.
- Purchase price and selling price are separate fields. There is no separate "dealer price" in the backend; the purchase price is what you pay your dealer or supplier.
- Missing cost data shows profit as "Unavailable", never 0. Stock value with missing costs is labelled as an estimate.
- B2B: sending reserves the seller's stock, and approval records the seller's sale and the buyer's purchase together.

## Feature parity with the Android app

Everything in the Android app's tabs and More menu is also on the website:

| App area | Web screen |
|---|---|
| Home dashboard, notifications | Overview, Notifications |
| Products: add/edit, custom fields, variants, images, batches & serials, archive | Products → product → Edit / Variants / Images |
| Stock: add, reduce, adjust, transfer, damaged, reserve/release, lot & expiry, serials | Inventory → Update stock |
| Stock activity | Inventory → Stock activity |
| Stock counts: snapshot, count, reconcile, cancel | Stock counts |
| Categories & fields, Units, unit conversions | Categories & fields (tabs) |
| Warehouses & branches, storage locations | Warehouses & branches → warehouse |
| Sales: record (with batch/serial), payments, cancel, returns, receipt | Sales → sale → Record return / Receipt (print or save as PDF) |
| Purchases: record (with lot/expiry/serial), payments, cancel | Purchases |
| Customers, suppliers | Customers, Suppliers |
| Business network: code, discoverable, search, connect, accept/reject/remove | Connected businesses |
| Business invoices: create, edit draft, send, cancel, approve/reject, returns | Business invoices |
| Product links | Product links |
| Reports & analytics with CSV/XLSX export | Reports |
| Employees: invite, role, deactivate, per-person permissions | Employees → employee |
| Business settings, logo | Business settings |
| Backup & restore | Backup & restore |
| Appearance & language (System/Light/Dark, 7 languages incl. RTL) | Appearance & language |
| Profile, change password, switch business | Profile, business menu |

The only app feature not copied is the **offline queue / pending sync**. It lets the phone record work without signal and send it later; a website works online, so it isn't needed there.

### Languages

The website uses the **same translations as the Android app**. Regenerate them after changing the app's strings:

```powershell
node scripts/build-i18n.mjs   # reads ../android/app/src/main/res/values*/strings*.xml → public/i18n/*.json
```

- English UI text is replaced with its translation as it appears on screen.
- Unknown text (product names, server messages) is left unchanged, as in the app.
- Labels that use the app's exact wording are translated. Web-only sentences stay in English until they're added to the app's string catalog.
- Arabic and Urdu switch the page to right-to-left.

## Troubleshooting

**"The inventory server is not reachable. Is the backend running?"**

The website can't reach the Spring backend at `API_BASE_URL`.

1. Check the backend: open `http://localhost:8082/v3/api-docs`. It should return JSON.
2. If you run the backend in Docker (`inventory-platform-backend`), its database must be running too. When the database is down, the backend restarts in a loop with `UnknownHostException: postgres`. From the repository root:
   ```powershell
   docker compose up -d postgres            # database
   docker compose --profile full up -d      # database + backend container
   docker logs --tail 50 inventory-platform-backend
   ```
   Or run the backend directly: `docker compose up -d postgres`, then `cd backend; .\gradlew.bat bootRun`.
3. Make sure `API_BASE_URL` in `.env.local` (or `.env`) matches the backend port (default `8082`), then restart `npm run dev`.

**Dev server shows "Internal Server Error" after a build**

`next dev` and `next build` share the `.next` folder, so don't run a production build while the dev server is running. To recover, stop the dev server, delete `.next`, and run `npm run dev` again.

## Known limitations

- **Contact form:** delivery needs `CONTACT_WEBHOOK_URL`. There is no email backend.
- **Password reset:** the backend has no email-based reset.
- **Unknown dashboard URLs:** they render the 404 page with `noindex`, but the HTTP status is 200 because the dashboard layout streams first.
- **Demo data:** fictional and in memory, and resets on reload. Exports are disabled in demo mode.
