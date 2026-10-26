import { AlertTriangle, Boxes, Package, PackageX } from "lucide-react";
import { StatusChip } from "@/components/ui/badge";

/**
 * Static, illustrative product preview for the landing page. Sample numbers only — clearly
 * labelled — and rendered as HTML (no screenshot) so it stays crisp and accessible.
 */
const BARS = [32, 41, 28, 46, 52, 38, 60, 55, 48, 66, 58, 71, 63, 74];

export function DashboardPreview() {
  return (
    <figure className="relative">
      <div className="overflow-hidden rounded-2xl border border-line bg-canvas shadow-[var(--shadow-pop)]">
        <div className="flex items-center gap-2 border-b border-line bg-white px-4 py-2.5">
          <span className="size-2.5 rounded-full bg-line-strong" aria-hidden />
          <span className="size-2.5 rounded-full bg-line-strong" aria-hidden />
          <span className="size-2.5 rounded-full bg-line-strong" aria-hidden />
          <span className="ml-3 truncate text-xs text-muted">Overview · Sample Wholesale Co.</span>
          <span className="ml-auto rounded-full bg-warning-bg px-2 py-0.5 text-[11px] font-semibold text-warning">Sample data</span>
        </div>
        <div className="grid gap-3 p-4 sm:grid-cols-4">
          {[
            { label: "Active products", value: "248", icon: Package, tone: "bg-brand-50 text-brand-600" },
            { label: "Stock quantity", value: "18,420", icon: Boxes, tone: "bg-accent-50 text-accent-700" },
            { label: "Low stock", value: "9", icon: AlertTriangle, tone: "bg-warning-bg text-warning" },
            { label: "Out of stock", value: "2", icon: PackageX, tone: "bg-danger-bg text-danger" },
          ].map((s) => (
            <div key={s.label} className="rounded-lg border border-line bg-white p-3">
              <div className="flex items-center justify-between">
                <p className="text-[11px] font-medium text-muted">{s.label}</p>
                <span className={`grid size-6 place-items-center rounded-md ${s.tone}`}><s.icon className="size-3.5" aria-hidden /></span>
              </div>
              <p className="mt-1 text-lg font-semibold tabular text-ink">{s.value}</p>
            </div>
          ))}
          <div className="rounded-lg border border-line bg-white p-3 sm:col-span-3">
            <p className="text-xs font-semibold text-ink">Net sales, last 14 days</p>
            <div className="mt-3 flex h-28 items-end gap-1.5" aria-hidden>
              {BARS.map((h, i) => (
                <div key={i} className="flex-1 rounded-t bg-brand-600" style={{ height: `${h}%`, opacity: i === BARS.length - 1 ? 1 : 0.8 }} />
              ))}
            </div>
          </div>
          <div className="rounded-lg border border-line bg-white p-3">
            <p className="text-xs font-semibold text-ink">Needs attention</p>
            <ul className="mt-2 space-y-2 text-[11px]">
              <li className="flex items-center justify-between gap-2"><span className="truncate">LED bulb 9W</span><StatusChip status="LOW_STOCK" /></li>
              <li className="flex items-center justify-between gap-2"><span className="truncate">Ball valve ½″</span><StatusChip status="OUT_OF_STOCK" /></li>
              <li className="flex items-center justify-between gap-2"><span className="truncate">Copper wire 2.5</span><StatusChip status="LOW_STOCK" /></li>
            </ul>
          </div>
        </div>
      </div>
      <figcaption className="mt-3 text-center text-xs text-muted">Illustrative sample data, not a real business.</figcaption>
    </figure>
  );
}
