"use client";

import { AlertTriangle, ArrowDownRight, ArrowUpRight, Boxes, FileText, IndianRupee, Package, PackagePlus, PackageX, ShoppingCart, Truck, TrendingUp } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { BarList, TrendChart } from "@/components/charts/trend-chart";
import { StatusChip } from "@/components/ui/badge";
import { ButtonLink } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Skeleton } from "@/components/ui/states";
import { api } from "@/lib/api/endpoints";
import type { Dashboard, Movement, Permission } from "@/lib/api/types";
import { firstName, humanize, isoDaysAgo, money, qty, relativeTime, today } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader, StatCard } from "../parts";

export function OverviewScreen() {
  const { client, me, href, can, currency } = useDashboard();
  const dash = useApi(() => api.dashboard(client), [client]);
  const d = dash.data;

  return (
    <>
      <PageHeader title={`Hello, ${firstName(me.fullName)}`} description="Here is how your stock looks today." />

      {dash.error ? (
        <Card>
          <ErrorState error={dash.error} onRetry={dash.reload} />
        </Card>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {!d ? (
              Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28 rounded-xl" />)
            ) : (
              <>
                <StatCard label="Total products" value={qty(d.activeProducts)} icon={<Package className="size-4" aria-hidden />} href={href("/products")} />
                <StatCard label="Total stock quantity" value={qty(d.totalQuantity)} hint="On hand, all units combined" icon={<Boxes className="size-4" aria-hidden />} tone="accent" href={href("/stock")} />
                <StatCard label="Low stock" value={d.lowStockCount} icon={<AlertTriangle className="size-4" aria-hidden />} tone="warning" href={href("/products?status=LOW_STOCK")} hint="At or below minimum" />
                <StatCard label="Out of stock" value={d.outOfStockCount} icon={<PackageX className="size-4" aria-hidden />} tone="danger" href={href("/products?status=OUT_OF_STOCK")} hint="Nothing available" />
              </>
            )}
          </div>

          <QuickActions />

          {d && d.activeProducts === 0 && (
            <Card>
              <EmptyState
                icon={PackagePlus}
                title="Get started by adding your first product"
                description="Add products with an opening quantity. Stock, sales and reports fill in from there."
                action={can("PRODUCT_CREATE") ? <ButtonLink href={href("/products/new")}>Add product</ButtonLink> : undefined}
              />
            </Card>
          )}

          {(can("SALE_RECORD") || can("PURCHASE_MANAGE")) && <TradeCard />}

          {d?.sales && <SalesHighlights d={d} currency={currency} />}
          {can("ANALYTICS_VIEW") && <TrendCard />}

          <div className="grid gap-6 lg:grid-cols-2">
            <NeedsAttention d={d} loading={dash.loading} />
            <Card>
              <CardHeader title="Stock by category" description="On-hand quantity in top-level categories" />
              <CardBody>
                {!d ? (
                  <LoadingBlock />
                ) : d.categories.length ? (
                  <BarList items={d.categories.slice(0, 6).map((c) => ({ label: c.categoryName ?? "Uncategorized", value: c.quantity, hint: `${c.productCount} products` }))} format={(v) => qty(v)} />
                ) : (
                  <EmptyState title="No categories yet" />
                )}
              </CardBody>
            </Card>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader title="Recent stock activity" action={<Link href={href("/stock")} className="text-sm font-medium text-brand-700 hover:underline">View all</Link>} />
              {!d ? <LoadingBlock /> : <MovementList movements={d.recentMovements.slice(0, 6)} />}
            </Card>
            <Card>
              <CardHeader title={d && d.warehouses.length > 1 ? "By warehouse" : "Most stocked"} />
              <CardBody>
                {!d ? (
                  <LoadingBlock rows={3} />
                ) : d.warehouses.length > 1 ? (
                  <BarList items={d.warehouses.map((w) => ({ label: w.warehouseName ?? "—", value: w.quantity, hint: `${w.productCount} products` }))} format={(v) => qty(v)} />
                ) : (
                  <BarList items={(d.mostStocked ?? []).map((p) => ({ label: p.productName, value: p.available, hint: p.unit }))} format={(v) => qty(v)} />
                )}
              </CardBody>
            </Card>
          </div>
        </div>
      )}
    </>
  );
}

function QuickActions() {
  const { href, can } = useDashboard();
  const actions: { label: string; path: string; icon: React.ReactNode; perm?: Permission[] }[] = [
    { label: "Add product", path: "/products/new", icon: <PackagePlus className="size-4" aria-hidden />, perm: ["PRODUCT_CREATE"] },
    { label: "Record sale", path: "/sales/new", icon: <ShoppingCart className="size-4" aria-hidden />, perm: ["SALE_RECORD"] },
    { label: "Record purchase", path: "/purchases/new", icon: <Truck className="size-4" aria-hidden />, perm: ["PURCHASE_MANAGE"] },
    { label: "New invoice", path: "/invoices/new", icon: <FileText className="size-4" aria-hidden />, perm: ["SALE_RECORD"] },
    { label: "Stock activity", path: "/stock", icon: <Boxes className="size-4" aria-hidden />, perm: ["STOCK_ADD", "STOCK_REDUCE", "STOCK_ADJUST"] },
    { label: "View Reports", path: "/reports", icon: <TrendingUp className="size-4" aria-hidden />, perm: ["REPORT_VIEW", "ANALYTICS_VIEW"] },
  ];
  const visible = actions.filter((a) => !a.perm || can(...a.perm));
  if (!visible.length) return null;
  return (
    <section aria-label="Quick actions" className="flex flex-wrap gap-2">
      {visible.map((a, i) => (
        <ButtonLink key={a.path} href={href(a.path)} variant={i === 0 ? "primary" : "secondary"} size="sm" icon={a.icon}>
          {a.label}
        </ButtonLink>
      ))}
    </section>
  );
}

function TradeCard() {
  const { client, href } = useDashboard();
  const s = useApi(() => api.trade.summary(client), [client]);
  if (s.error || !s.data) return null;
  const t = s.data;
  const items = [
    { label: "Purchase invoices need your approval", value: t.pendingApprovals, path: "/invoices?direction=PURCHASES" },
    { label: "Sent invoices awaiting the buyer", value: t.awaitingBuyer, path: "/invoices" },
    { label: "Draft invoices", value: t.drafts, path: "/invoices?status=DRAFT" },
    { label: "Returns to review", value: t.returnsToReview, path: "/invoices" },
  ].filter((i) => i.value > 0);
  return (
    <Card>
      <CardHeader title="Business network" description={`${t.connectedBusinesses} connected business${t.connectedBusinesses === 1 ? "" : "es"}`} action={<Link href={href("/invoices")} className="text-sm font-medium text-brand-700 hover:underline">Business invoices</Link>} />
      <CardBody className="py-4">
        {items.length ? (
          <ul className="grid gap-2 sm:grid-cols-2">
            {items.map((i) => (
              <li key={i.label}>
                <Link href={href(i.path)} className="flex items-center justify-between rounded-lg border border-line px-3 py-2 text-sm hover:bg-brand-50">
                  <span>{i.label}</span>
                  <span className="rounded-full bg-brand-600 px-2 text-xs font-semibold text-white tabular">{i.value}</span>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted">Nothing needs your attention in the business network.</p>
        )}
      </CardBody>
    </Card>
  );
}

function SalesHighlights({ d, currency }: { d: Dashboard; currency: string }) {
  const s = d.sales!;
  const profit = (v?: number) => (v === undefined ? "Unavailable" : money(v, currency));
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="grid grid-cols-2 gap-3 lg:col-span-2">
        <StatCard label="Today's sales" value={money(s.today.revenue, currency)} hint={`${s.today.sales} sale${s.today.sales === 1 ? "" : "s"}`} icon={<IndianRupee className="size-4" aria-hidden />} />
        <StatCard label="This month's sales" value={money(s.month.revenue, currency)} hint={`${s.month.sales} sales · ${qty(s.month.units)} units`} icon={<IndianRupee className="size-4" aria-hidden />} />
        <StatCard label="Gross profit this month" value={profit(s.month.grossProfit)} hint={s.month.grossProfit === undefined ? "Some sold items have no cost recorded" : "Net sales − cost of goods sold"} icon={<TrendingUp className="size-4" aria-hidden />} tone="accent" />
        <StatCard
          label="Stock value at cost"
          value={money(s.inventory.valueAtCost, currency)}
          hint={s.inventory.productsWithoutCost ? `Estimate: ${s.inventory.productsWithoutCost} products have no cost` : "Available stock × average cost"}
          icon={<Boxes className="size-4" aria-hidden />}
          tone="accent"
        />
      </div>
      <Card>
        <CardHeader title="This month" />
        <CardBody className="space-y-3 text-sm">
          <Row label="Best seller" value={s.monthBestSeller ? `${s.monthBestSeller.name} · ${qty(s.monthBestSeller.units)} ${s.monthBestSeller.unit}` : "No sales yet"} icon={<ArrowUpRight className="size-4 text-success" aria-hidden />} />
          <Row label="Lowest seller" value={s.monthLowestSeller ? `${s.monthLowestSeller.name} · ${qty(s.monthLowestSeller.units)} ${s.monthLowestSeller.unit}` : "—"} icon={<ArrowDownRight className="size-4 text-warning" aria-hidden />} />
          <Row label="Products with no sales" value={String(s.monthProductsWithNoSales)} />
          <Row label="Profit if remaining stock sells" value={money(s.inventory.potentialProfit, currency)} />
        </CardBody>
      </Card>
    </div>
  );
}

function Row({ label, value, icon }: { label: string; value: string; icon?: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="flex items-center gap-1.5 text-muted">
        {icon}
        {label}
      </span>
      <span className="text-right font-medium text-ink">{value}</span>
    </div>
  );
}

function TrendCard() {
  const { client, currency, can } = useDashboard();
  const [days, setDays] = useState(30);
  const trend = useApi(() => api.analytics.trend(client, { from: isoDaysAgo(days - 1), to: today(), granularity: "day" }), [client, days]);
  return (
    <Card>
      <CardHeader
        title="Sales trends"
        description="Completed sales only; returns are subtracted from the original sale's period."
        action={
          <>
            <label htmlFor="trend-period" className="sr-only">
              Period
            </label>
            <Select id="trend-period" value={days} onChange={(e) => setDays(Number(e.target.value))} className="h-9 w-36!">
              <option value={7}>Last 7 days</option>
              <option value={30}>Last 30 days</option>
              <option value={90}>Last 90 days</option>
            </Select>
          </>
        }
      />
      <CardBody>
        {trend.error ? <ErrorState error={trend.error} onRetry={trend.reload} /> : !trend.data ? <Skeleton className="h-64" /> : <TrendChart points={trend.data.points} currency={currency} showProfit={can("ANALYTICS_VIEW")} />}
      </CardBody>
    </Card>
  );
}

function NeedsAttention({ d, loading }: { d?: Dashboard; loading: boolean }) {
  const { href } = useDashboard();
  const items = d ? [...d.outOfStockItems, ...d.lowStockItems].slice(0, 6) : [];
  return (
    <Card>
      <CardHeader title="Needs attention" description="Out-of-stock and low-stock products" action={<Link href={href("/products?status=LOW_STOCK")} className="text-sm font-medium text-brand-700 hover:underline">View all</Link>} />
      {loading && !d ? (
        <LoadingBlock />
      ) : items.length === 0 ? (
        <EmptyState title="All stock levels look healthy" description="Nothing is at or below its minimum." />
      ) : (
        <ul className="divide-y divide-line">
          {items.map((i) => (
            <li key={i.productId} className="flex items-center justify-between gap-3 px-5 py-3">
              <div className="min-w-0">
                <Link href={href(`/products/${i.productId}`)} className="block truncate text-sm font-medium text-ink hover:text-brand-700 hover:underline">
                  {i.productName}
                </Link>
                <p className="text-xs text-muted tabular">
                  {qty(i.available)} {i.unit} available · minimum {qty(i.minStock)}
                </p>
              </div>
              <StatusChip status={i.status} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

export function MovementList({ movements }: { movements: Movement[] }) {
  if (!movements.length) return <EmptyState title="No stock activity yet" />;
  return (
    <ul className="divide-y divide-line">
      {movements.map((m) => (
        <li key={m.id} className="flex items-center justify-between gap-3 px-5 py-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{m.productName}</p>
            <p className="truncate text-xs text-muted">
              {humanize(m.type)} · {m.warehouseName}
              {m.actorName ? ` · ${m.actorName}` : ""} · {relativeTime(m.createdAt)}
            </p>
          </div>
          <div className="shrink-0 text-right">
            <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-semibold tabular ${m.quantity >= 0 ? "bg-success-bg text-success" : "bg-danger-bg text-danger"}`}>
              {m.quantity > 0 ? "+" : ""}
              {qty(m.quantity)} {m.unitCode}
            </span>
            {m.bucket === "ON_HAND" && (
              <p className="mt-0.5 text-[11px] text-muted tabular">
                {qty(m.quantityBefore)} → {qty(m.quantityAfter)}
              </p>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
