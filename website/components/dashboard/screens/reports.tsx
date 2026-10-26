"use client";

import { Download, Lightbulb, TrendingDown, TrendingUp } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { BarList, TrendChart } from "@/components/charts/trend-chart";
import { Badge, StatusChip } from "@/components/ui/badge";
import { buttonClass } from "@/components/ui/button";
import { Card, CardBody, CardHeader } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock, Notice, Skeleton } from "@/components/ui/states";
import { DataTable } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import type { Query } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Metric, ProductPerformance } from "@/lib/api/types";
import { date, humanize, isoDaysAgo, money, percent, qty, signedPercent, today } from "@/lib/format";
import { useApi, useDashboard } from "../context";
import { PageHeader, StatCard } from "../parts";

type Tab = "overview" | "products" | "categories" | "stock";

export function ReportsScreen() {
  const { can } = useDashboard();
  const analytics = can("ANALYTICS_VIEW");
  const tabs: { key: Tab; label: string }[] = [
    ...(analytics ? [{ key: "overview" as const, label: "Sales overview" }, { key: "products" as const, label: "Product performance" }, { key: "categories" as const, label: "Categories" }] : []),
    ...(can("REPORT_VIEW") ? [{ key: "stock" as const, label: "Stock movement" }] : []),
  ];
  const [tab, setTab] = useState<Tab>(tabs[0]?.key ?? "stock");
  const [from, setFrom] = useState(isoDaysAgo(29));
  const [to, setTo] = useState(today());
  const range = { from, to };

  return (
    <>
      <PageHeader
        title="Reports"
        description="Revenue means net sales: gross sales − discounts − returns, excluding tax. Each period is compared with the previous period of the same length."
        actions={
          tab !== "stock" && (
            <div className="flex flex-wrap items-center gap-2">
              <label htmlFor="r-from" className="text-sm text-muted">From</label>
              <Input id="r-from" type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} className="w-40!" />
              <label htmlFor="r-to" className="text-sm text-muted">to</label>
              <Input id="r-to" type="date" value={to} min={from} max={today()} onChange={(e) => e.target.value && setTo(e.target.value)} className="w-40!" />
            </div>
          )
        }
      />
      {tabs.length === 0 ? (
        <Notice tone="warning">You don&apos;t have permission to view reports.</Notice>
      ) : (
        <>
          <div className="mb-6">
            <Tabs label="Report sections" value={tab} onChange={setTab} tabs={tabs} />
          </div>
          {tab === "overview" && <OverviewTab range={range} />}
          {tab === "products" && <ProductsTab range={range} />}
          {tab === "categories" && <CategoriesTab range={range} />}
          {tab === "stock" && <StockTab />}
        </>
      )}
    </>
  );
}

function Growth({ m }: { m: Metric }) {
  if (m.growthPercent === undefined || m.growthPercent === null) return <span>Insufficient data to compare</span>;
  const up = m.growthPercent >= 0;
  return (
    <span className={`inline-flex items-center gap-1 ${up ? "text-success" : "text-danger"}`}>
      {up ? <TrendingUp className="size-3.5" aria-hidden /> : <TrendingDown className="size-3.5" aria-hidden />}
      {signedPercent(m.growthPercent)} vs previous period
    </span>
  );
}

function OverviewTab({ range }: { range: Query }) {
  const { client, currency, href } = useDashboard();
  const o = useApi(() => api.analytics.overview(client, range), [client, range.from, range.to]);
  const trend = useApi(() => api.analytics.trend(client, { ...range, granularity: "day" }), [client, range.from, range.to]);
  const insights = useApi(() => api.analytics.insights(client, range), [client, range.from, range.to]);
  if (o.error) return <Card><ErrorState error={o.error} onRetry={o.reload} /></Card>;
  const d = o.data;
  const f = d?.financials;
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {!d ? Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28 rounded-xl" />) : (
          <>
            <StatCard label="Net sales" value={money(d.revenue.value, currency)} hint={<Growth m={d.revenue} />} />
            <StatCard label="Gross profit" value={f?.profitAvailable ? money(d.grossProfit.value, currency) : "Unavailable"} hint={f?.profitAvailable ? <Growth m={d.grossProfit} /> : `${f?.linesWithoutCost} sold lines have no cost`} tone="accent" />
            <StatCard label="Sales" value={qty(d.sales.value)} hint={<Growth m={d.sales} />} />
            <StatCard label="Units sold" value={qty(d.units.value)} hint={<Growth m={d.units} />} />
          </>
        )}
      </div>
      <Card>
        <CardHeader title="Daily trend" />
        <CardBody>{trend.error ? <ErrorState error={trend.error} onRetry={trend.reload} /> : !trend.data ? <Skeleton className="h-64" /> : <TrendChart points={trend.data.points} currency={currency} showProfit />}</CardBody>
      </Card>
      {d && f && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card>
            <CardHeader title="Financials" />
            <CardBody>
              <dl className="space-y-1.5 text-sm">
                {[
                  ["Gross sales", money(f.grossSales, currency)],
                  ["Discounts", `−${money(f.discounts, currency)}`],
                  ["Returns", `−${money(f.returns, currency)}`],
                  ["Net sales", money(f.netSales, currency)],
                  ["Tax collected", money(f.taxCollected, currency)],
                  ["Cost of goods sold", f.costOfGoodsSold === undefined ? "Unavailable" : money(f.costOfGoodsSold, currency)],
                  ["Gross profit", f.grossProfit === undefined ? "Unavailable" : money(f.grossProfit, currency)],
                  ["Margin", f.marginPercent === undefined ? "—" : percent(f.marginPercent)],
                ].map(([k, v]) => (
                  <div key={k} className="flex justify-between gap-3"><dt className="text-muted">{k}</dt><dd className="tabular">{v}</dd></div>
                ))}
              </dl>
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Inventory value" description="Available stock only" />
            <CardBody>
              <dl className="space-y-1.5 text-sm">
                <div className="flex justify-between"><dt className="text-muted">At cost</dt><dd className="tabular">{money(d.inventory.valueAtCost, currency)}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">At selling price</dt><dd className="tabular">{money(d.inventory.valueAtSellingPrice, currency)}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Potential profit</dt><dd className="tabular">{money(d.inventory.potentialProfit, currency)}</dd></div>
              </dl>
              {(d.inventory.productsWithoutCost > 0 || d.inventory.productsWithoutPrice > 0) && (
                <p className="mt-3 text-xs text-warning">Estimate: {d.inventory.productsWithoutCost} products without cost and {d.inventory.productsWithoutPrice} without a selling price are excluded.</p>
              )}
            </CardBody>
          </Card>
          <Card>
            <CardHeader title="Purchases" />
            <CardBody>
              <dl className="space-y-1.5 text-sm">
                <div className="flex justify-between"><dt className="text-muted">Purchases recorded</dt><dd className="tabular">{d.purchases.purchases}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Units received</dt><dd className="tabular">{qty(d.purchases.unitsReceived)}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Total spent</dt><dd className="tabular">{money(d.purchases.totalSpent, currency)}</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Previous period</dt><dd className="tabular">{money(d.purchases.previousTotalSpent, currency)}</dd></div>
              </dl>
            </CardBody>
          </Card>
        </div>
      )}
      <Card>
        <CardHeader title="Insights" description="Plain-language findings for this period" />
        {insights.error ? <ErrorState error={insights.error} onRetry={insights.reload} /> : !insights.data ? <LoadingBlock rows={3} /> : insights.data.insights.length === 0 ? <EmptyState icon={Lightbulb} title="No insights for this period" /> : (
          <ul className="divide-y divide-line">
            {insights.data.insights.map((i, k) => (
              <li key={k} className="flex items-start gap-3 px-5 py-3 text-sm">
                <Badge tone={i.severity === "GOOD" ? "success" : i.severity === "WARNING" ? "warning" : "info"}>{i.title}</Badge>
                <p className="flex-1">{i.message}</p>
                {i.productId && <Link href={href(`/products/${i.productId}`)} className="shrink-0 font-medium text-brand-700 hover:underline">{i.action === "ADD_STOCK" ? "Restock" : "View"}</Link>}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function ProductsTab({ range }: { range: Query }) {
  const { client, currency, href, can } = useDashboard();
  const [sort, setSort] = useState("units");
  const rows = useApi(() => api.analytics.products(client, { ...range, sort }), [client, range.from, range.to, sort]);
  const exportUrl = can("REPORT_EXPORT") ? client.exportUrl("/analytics/products/export", { ...range, sort, format: "xlsx" }) : null;
  return (
    <Card>
      <CardHeader
        title="Product performance"
        description="Ranked by the chosen measure. Slow movers and products with no sales are labelled."
        action={
          <>
            <label htmlFor="pp-sort" className="sr-only">Sort by</label>
            <Select id="pp-sort" value={sort} onChange={(e) => setSort(e.target.value)} className="h-9 w-40!">
              <option value="units">Units sold</option>
              <option value="revenue">Net sales</option>
              <option value="profit">Gross profit</option>
              <option value="growth">Growth</option>
              <option value="name">Name</option>
            </Select>
            <ExportLink url={exportUrl} />
          </>
        }
      />
      {rows.error ? <ErrorState error={rows.error} onRetry={rows.reload} /> : !rows.data ? <LoadingBlock rows={6} /> : rows.data.products.length === 0 ? <EmptyState title="No products to show" /> : (
        <DataTable<ProductPerformance>
          caption="Product performance"
          rows={rows.data.products}
          rowKey={(r) => r.productId}
          rowHref={(r) => href(`/products/${r.productId}`)}
          columns={[
            { header: "Product", cell: (r) => r.name },
            { header: "Units", align: "right", cell: (r) => `${qty(r.unitsSold)} ${r.unit}` },
            { header: "Net sales", align: "right", cell: (r) => money(r.revenue, currency) },
            { header: "Gross profit", align: "right", cell: (r) => (r.grossProfit === undefined ? "—" : money(r.grossProfit, currency)), hideOnMobile: true },
            { header: "Growth", align: "right", cell: (r) => (r.growthPercent === undefined ? humanize(r.performance) : signedPercent(r.growthPercent)), hideOnMobile: true },
            { header: "Stock", cell: (r) => <StatusChip status={r.stockStatus} />, hideOnMobile: true },
            { header: "Labels", cell: (r) => <div className="flex flex-wrap gap-1">{r.labels.map((l) => <Badge key={l} tone={l === "TOP_SELLER" || l === "FAST_MOVING" ? "success" : l === "NO_SALES" ? "danger" : "warning"}>{humanize(l)}</Badge>)}</div> },
          ]}
        />
      )}
    </Card>
  );
}

function CategoriesTab({ range }: { range: Query }) {
  const { client, currency } = useDashboard();
  const rows = useApi(() => api.analytics.categories(client, range), [client, range.from, range.to]);
  return (
    <Card>
      <CardHeader title="Net sales by category" />
      <CardBody>
        {rows.error ? <ErrorState error={rows.error} onRetry={rows.reload} /> : !rows.data ? <LoadingBlock /> : rows.data.categories.length === 0 ? <EmptyState title="No sales in this period" /> : (
          <BarList items={rows.data.categories.map((c) => ({ label: c.categoryName, value: c.revenue, hint: `${qty(c.units)} units${c.growthPercent !== undefined ? ` · ${signedPercent(c.growthPercent)}` : ""}` }))} format={(v) => money(v, currency)} />
        )}
      </CardBody>
    </Card>
  );
}

function StockTab() {
  const { client, can, href } = useDashboard();
  const s = useApi(() => api.reports.movementSummary(client, {}), [client]);
  const exp = (report: string, format: string) => (can("REPORT_EXPORT") ? client.exportUrl(`/reports/${report}/export`, { format }) : null);
  return (
    <div className="space-y-6">
      {s.error ? <Card><ErrorState error={s.error} onRetry={s.reload} /></Card> : !s.data ? <Card><LoadingBlock /></Card> : (
        <>
          <p className="text-sm text-muted">Last 30 days: {date(s.data.from)} – {date(s.data.to)}</p>
          <div className="grid grid-cols-3 gap-3">
            <StatCard label="Stock in" value={qty(s.data.totalIn)} tone="accent" />
            <StatCard label="Stock out" value={qty(s.data.totalOut)} tone="warning" />
            <StatCard label="Movements" value={s.data.movementCount} />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader title="Fast-moving products" />
              <ul className="divide-y divide-line">{s.data.fastMoving.map((p) => <li key={p.productId} className="flex justify-between gap-3 px-5 py-2.5 text-sm"><Link href={href(`/products/${p.productId}`)} className="truncate hover:underline">{p.productName}</Link><span className="shrink-0 tabular text-muted">{qty(p.quantityOut)} {p.unit} out · {qty(p.available)} left</span></li>)}</ul>
            </Card>
            <Card>
              <CardHeader title="Slow-moving products" />
              <ul className="divide-y divide-line">{s.data.slowMoving.map((p) => <li key={p.productId} className="flex justify-between gap-3 px-5 py-2.5 text-sm"><Link href={href(`/products/${p.productId}`)} className="truncate hover:underline">{p.productName}</Link><span className="shrink-0 tabular text-muted">{qty(p.quantityOut)} {p.unit} out · {qty(p.available)} left</span></li>)}</ul>
            </Card>
          </div>
          <Card>
            <CardHeader title="By movement type" />
            <CardBody><BarList items={s.data.byType.map((b) => ({ label: humanize(b.type), value: Math.abs(b.quantity), hint: `${b.count} movements` }))} format={(v) => qty(v)} /></CardBody>
          </Card>
        </>
      )}
      <Card>
        <CardHeader title="Export reports" description="CSV or Excel files with the current data." />
        <CardBody className="flex flex-wrap gap-2">
          {can("REPORT_EXPORT") ? (
            client.mode === "demo" ? <p className="text-sm text-muted">Exports are not available in demo mode.</p> : (
              ["inventory", "low-stock", "movements"].map((r) => (
                <span key={r} className="inline-flex gap-1">
                  <a className={buttonClass("secondary", "sm")} href={exp(r, "csv") ?? undefined} download>{humanize(r.replace("-", "_"))} CSV</a>
                  <a className={buttonClass("secondary", "sm")} href={exp(r, "xlsx") ?? undefined} download>XLSX</a>
                </span>
              ))
            )
          ) : (
            <p className="text-sm text-muted">Exporting needs the “Export reports” permission.</p>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

function ExportLink({ url }: { url: string | null }) {
  if (!url) return null;
  return (
    <a href={url} download className={buttonClass("secondary", "sm")}>
      <Download className="size-4" aria-hidden /> Export
    </a>
  );
}
