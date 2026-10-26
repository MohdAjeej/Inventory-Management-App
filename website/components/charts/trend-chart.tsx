"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { TrendPoint } from "@/lib/api/types";
import { date, money } from "@/lib/format";

// Validated pair (dataviz validator: CVD ΔE 16.1, contrast ≥ 3:1 on white).
const REVENUE = "#1F5FAD";
const PROFIT = "#00897B";

/**
 * Net sales and gross profit per day on one currency axis. Includes a legend (2 series),
 * a hover tooltip, and a visually hidden table so the numbers are available without the chart.
 */
export function TrendChart({ points, currency, showProfit }: { points: TrendPoint[]; currency: string; showProfit: boolean }) {
  const totalRevenue = points.reduce((a, p) => a + p.revenue, 0);
  const hasProfit = showProfit && points.some((p) => p.grossProfit !== undefined);
  return (
    <figure>
      <div className="mb-3 flex flex-wrap items-center gap-4 text-xs text-ink-2" aria-hidden>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-0.5 w-4 rounded" style={{ background: REVENUE }} /> Net sales
        </span>
        {hasProfit && (
          <span className="inline-flex items-center gap-1.5">
            <span className="h-0.5 w-4 rounded" style={{ background: PROFIT }} /> Gross profit
          </span>
        )}
      </div>
      <div className="h-64 w-full" aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={points} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="rev" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={REVENUE} stopOpacity={0.16} />
                <stop offset="100%" stopColor={REVENUE} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="#e8ecf1" vertical={false} />
            <XAxis dataKey="start" tickFormatter={(v: string) => date(v).replace(/ \d{4}$/, "")} tick={{ fontSize: 11, fill: "#55606e" }} tickLine={false} axisLine={{ stroke: "#dde3ea" }} minTickGap={24} />
            <YAxis tickFormatter={(v: number) => money(v, currency, { compact: true })} tick={{ fontSize: 11, fill: "#55606e" }} tickLine={false} axisLine={false} width={64} />
            <Tooltip
              cursor={{ stroke: "#8a94a3", strokeDasharray: "3 3" }}
              contentStyle={{ borderRadius: 8, border: "1px solid #dde3ea", fontSize: 12, color: "#15191f" }}
              labelFormatter={(v) => date(String(v))}
              formatter={(v, name) => [money(Number(v), currency), name === "revenue" ? "Net sales" : "Gross profit"]}
            />
            <Area isAnimationActive={false} type="monotone" dataKey="revenue" stroke={REVENUE} strokeWidth={2} fill="url(#rev)" activeDot={{ r: 4, strokeWidth: 2, stroke: "#fff" }} />
            {hasProfit && <Area isAnimationActive={false} type="monotone" dataKey="grossProfit" stroke={PROFIT} strokeWidth={2} fill="none" activeDot={{ r: 4, strokeWidth: 2, stroke: "#fff" }} />}
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="sr-only">
        Daily net sales{hasProfit ? " and gross profit" : ""} from {date(points[0]?.start)} to {date(points.at(-1)?.start)}. Total net sales {money(totalRevenue, currency)}.
      </figcaption>
      <details className="mt-2 text-sm">
        <summary className="cursor-pointer text-xs font-medium text-brand-700">Show data table</summary>
        <div className="mt-2 max-h-56 overflow-y-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="text-left text-muted">
                <th scope="col" className="py-1">Date</th>
                <th scope="col" className="py-1 text-right">Sales</th>
                <th scope="col" className="py-1 text-right">Net sales</th>
                {hasProfit && <th scope="col" className="py-1 text-right">Gross profit</th>}
              </tr>
            </thead>
            <tbody>
              {points.map((p) => (
                <tr key={p.start} className="border-t border-line">
                  <td className="py-1">{date(p.start)}</td>
                  <td className="py-1 text-right tabular">{p.sales}</td>
                  <td className="py-1 text-right tabular">{money(p.revenue, currency)}</td>
                  {hasProfit && <td className="py-1 text-right tabular">{p.grossProfit === undefined ? "Unavailable" : money(p.grossProfit, currency)}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}

/** Horizontal bars for a single measure (e.g. stock by category). Plain HTML: accessible by default. */
export function BarList({ items, format }: { items: { label: string; value: number; hint?: string }[]; format: (v: number) => string }) {
  const max = Math.max(...items.map((i) => i.value), 1);
  return (
    <ul className="space-y-3">
      {items.map((i) => (
        <li key={i.label}>
          <div className="mb-1 flex justify-between gap-3 text-sm">
            <span className="truncate text-ink">{i.label}</span>
            <span className="shrink-0 text-ink-2 tabular">
              {format(i.value)}
              {i.hint && <span className="text-muted"> · {i.hint}</span>}
            </span>
          </div>
          <div className="h-2 rounded-full bg-subtle" aria-hidden>
            <div className="h-2 rounded-full bg-brand-600" style={{ width: `${Math.max((i.value / max) * 100, 1.5)}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );
}
