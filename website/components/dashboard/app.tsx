"use client";

import { Suspense } from "react";
import { ErrorState, Spinner } from "@/components/ui/states";
import { ToastProvider } from "@/components/ui/toast";
import { Logo } from "@/components/logo";
import { matchScreen } from "@/lib/routes";
import { DashboardProvider } from "./context";
import { DashboardShell } from "./shell";
import { StockScreen } from "./screens/inventory";
import { CatalogScreen } from "./screens/catalog";
import { WarehouseDetailScreen, WarehousesScreen } from "./screens/warehouses";
import { StockCountDetailScreen, StockCountsScreen } from "./screens/counts";
import { ProductFormScreen } from "./screens/product-form";
import { SaleReceiptScreen, SaleReturnScreen } from "./screens/sale-extras";
import { InvoiceFormScreen } from "./screens/invoice-extras";
import { NetworkScreen, ProductLinksScreen } from "./screens/network";
import { AppearanceScreen, BackupScreen, EmployeeDetailScreen } from "./screens/account-extras";
import { EmployeesScreen, NotificationsScreen, ProfileScreen, SettingsScreen } from "./screens/account";
import { InvoiceDetailScreen, InvoicesScreen } from "./screens/invoices";
import { OverviewScreen } from "./screens/overview";
import { PartiesScreen } from "./screens/parties";
import { ProductDetailScreen, ProductsScreen } from "./screens/products";
import { ReportsScreen } from "./screens/reports";
import { PurchaseDetailScreen, PurchasesScreen, SaleDetailScreen, SalesScreen, TransactionFormScreen } from "./screens/trade";

export function DashboardScreen({ slug }: { slug: string[] }) {
  const match = matchScreen(slug);
  if (!match) return null;
  const id = match.id ?? 0;
  switch (match.key) {
    case "overview": return <OverviewScreen />;
    case "products": return <ProductsScreen />;
    case "product-new": return <ProductFormScreen />;
    case "product-detail": return <ProductDetailScreen id={id} />;
    case "catalog": return <CatalogScreen />;
    case "stock": return <StockScreen />;
    case "warehouses": return <WarehousesScreen />;
    case "sales": return <SalesScreen />;
    case "sale-new": return <TransactionFormScreen kind="sale" />;
    case "sale-detail": return <SaleDetailScreen id={id} />;
    case "purchases": return <PurchasesScreen />;
    case "purchase-new": return <TransactionFormScreen kind="purchase" />;
    case "purchase-detail": return <PurchaseDetailScreen id={id} />;
    case "customers": return <PartiesScreen kind="customers" />;
    case "suppliers": return <PartiesScreen kind="suppliers" />;
    case "invoices": return <InvoicesScreen />;
    case "invoice-new": return <InvoiceFormScreen />;
    case "invoice-detail": return <InvoiceDetailScreen id={id} />;
    case "reports": return <ReportsScreen />;
    case "employees": return <EmployeesScreen />;
    case "settings": return <SettingsScreen />;
    case "profile": return <ProfileScreen />;
    case "notifications": return <NotificationsScreen />;
    case "product-edit": return <ProductFormScreen id={id} />;
    case "stock-counts": return <StockCountsScreen />;
    case "stock-count-detail": return <StockCountDetailScreen id={id} />;
    case "warehouse-detail": return <WarehouseDetailScreen id={id} />;
    case "sale-return": return <SaleReturnScreen id={id} />;
    case "sale-receipt": return <SaleReceiptScreen id={id} />;
    case "invoice-edit": return <InvoiceFormScreen id={id} />;
    case "network": return <NetworkScreen />;
    case "product-links": return <ProductLinksScreen />;
    case "employee-detail": return <EmployeeDetailScreen id={id} />;
    case "backup": return <BackupScreen />;
    case "appearance": return <AppearanceScreen />;
  }
}

/** Lives in the route-group layout so the session and shell persist across screen navigation. */
export function DashboardLayout({ mode, children }: { mode: "live" | "demo"; children: React.ReactNode }) {
  return (
    <ToastProvider>
      <DashboardProvider
        mode={mode}
        fallback={({ error, retry }) => (
          <div className="app-theme grid min-h-dvh place-items-center bg-canvas p-6 text-ink">
            <div className="w-full max-w-md text-center">
              <div className="mb-6 flex justify-center"><Logo /></div>
              {error ? <div className="rounded-xl border border-line bg-surface"><ErrorState error={error} onRetry={retry} /></div> : <Spinner label="Loading your workspace…" />}
            </div>
          </div>
        )}
      >
        <DashboardShell>
          <Suspense fallback={<Spinner />}>
            {children}
          </Suspense>
        </DashboardShell>
      </DashboardProvider>
    </ToastProvider>
  );
}
