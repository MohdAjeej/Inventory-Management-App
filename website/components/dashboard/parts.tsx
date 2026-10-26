"use client";

import { clsx } from "clsx";
import { ArrowLeft, Lock, Search } from "lucide-react";
import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { Notice } from "@/components/ui/states";
import { api } from "@/lib/api/endpoints";
import type { ProductDetail, ProductSummary, Variant } from "@/lib/api/types";
import { qty } from "@/lib/format";
import { useDashboard, useDebounced } from "./context";

export function PageHeader({ title, description, actions, back }: { title: string; description?: ReactNode; actions?: ReactNode; back?: { href: string; label: string } }) {
  return (
    <div className="mb-6">
      {back && (
        <Link href={back.href} className="mb-2 inline-flex items-center gap-1 text-sm font-medium text-muted hover:text-ink">
          <ArrowLeft className="size-4" aria-hidden /> {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
          {description && <p className="mt-1 text-sm text-muted">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}

export function StatCard({ label, value, hint, icon, tone = "brand", href }: { label: string; value: ReactNode; hint?: ReactNode; icon?: ReactNode; tone?: "brand" | "accent" | "warning" | "danger"; href?: string }) {
  const toneCls = { brand: "bg-brand-50 text-brand-600", accent: "bg-accent-50 text-accent-700", warning: "bg-warning-bg text-warning", danger: "bg-danger-bg text-danger" }[tone];
  const body = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-medium text-muted">{label}</p>
        {icon && <span className={clsx("grid size-8 shrink-0 place-items-center rounded-lg", toneCls)}>{icon}</span>}
      </div>
      <p className="mt-2 text-2xl font-semibold tracking-tight text-ink tabular">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </>
  );
  const cls = "block rounded-xl border border-line bg-surface p-4 shadow-[var(--shadow-card)]";
  return href ? (
    <Link href={href} className={clsx(cls, "transition-colors hover:border-brand-200 hover:bg-brand-50/40")}>
      {body}
    </Link>
  ) : (
    <div className={cls}>{body}</div>
  );
}

export function SearchBox({ value, onChange, placeholder, label }: { value: string; onChange: (v: string) => void; placeholder: string; label: string }) {
  return (
    <div className="relative w-full sm:max-w-xs">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
      <input
        type="search"
        aria-label={label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="h-10 w-full rounded-lg border border-line-strong bg-surface pl-9 pr-3 text-sm shadow-sm placeholder:text-muted/70 focus:border-brand-600 focus:outline-2 focus:outline-brand-100"
      />
    </div>
  );
}

/** Shown where the web doesn't implement a write action yet, instead of a dead button. */
export function MobileOnlyNote({ children }: { children: ReactNode }) {
  return (
    <Notice tone="info" icon={<Lock className="mt-0.5 size-4 shrink-0" aria-hidden />}>
      {children}
    </Notice>
  );
}

export interface PickedVariant {
  product: ProductDetail;
  variant: Variant;
}

/** Search products by name/SKU/barcode, then pick the exact variant (sales, purchases, invoices, stock). */
export function ProductPicker({ onPick, label = "Add product", excludeVariantIds = [] }: { onPick: (p: PickedVariant) => void; label?: string; excludeVariantIds?: number[] }) {
  const { client } = useDashboard();
  const [q, setQ] = useState("");
  const dq = useDebounced(q, 250);
  const [results, setResults] = useState<ProductSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ProductDetail | null>(null);
  const [error, setError] = useState<string>();

  useEffect(() => {
    if (dq.trim().length < 1) {
      setResults([]);
      return;
    }
    let active = true;
    setLoading(true);
    api.products
      .list(client, { q: dq, size: 8 })
      .then((p) => active && setResults(p.content))
      .catch((e) => active && setError(e.message))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [dq, client]);

  const choose = async (id: number) => {
    setError(undefined);
    try {
      const d = await api.products.get(client, id);
      const variants = d.variants.filter((v) => !v.archived && !excludeVariantIds.includes(v.id));
      if (variants.length === 1) {
        onPick({ product: d, variant: variants[0] });
        setQ("");
        setResults([]);
      } else if (variants.length === 0) {
        setError("That product is already on the list.");
      } else setDetail(d);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="space-y-2">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
        <input
          type="search"
          aria-label={label}
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setDetail(null);
          }}
          placeholder={`${label}: search name, SKU or barcode`}
          className="h-10 w-full rounded-lg border border-line-strong bg-surface pl-9 pr-3 text-sm shadow-sm focus:border-brand-600 focus:outline-2 focus:outline-brand-100"
        />
      </div>
      {error && <p className="text-xs font-medium text-danger">{error}</p>}
      {loading && <p className="text-xs text-muted">Searching…</p>}
      {!detail && results.length > 0 && (
        <ul className="max-h-64 divide-y divide-line overflow-y-auto rounded-lg border border-line bg-surface">
          {results.map((r) => (
            <li key={r.id}>
              <button type="button" onClick={() => choose(r.id)} className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-brand-50">
                <span className="min-w-0">
                  <span className="block truncate font-medium">{r.name}</span>
                  <span className="text-xs text-muted">{r.sku ?? "No SKU"}</span>
                </span>
                <span className="shrink-0 text-xs text-muted tabular">
                  {qty(r.stock.available)} {r.unitCode} available
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {!loading && dq && results.length === 0 && !detail && <p className="text-xs text-muted">No matching products.</p>}
      {detail && (
        <div className="rounded-lg border border-line p-3">
          <p className="mb-2 text-sm font-medium">Choose a variant of {detail.name}</p>
          <div className="flex flex-wrap gap-2">
            {detail.variants
              .filter((v) => !v.archived && !excludeVariantIds.includes(v.id))
              .map((v) => (
                <button
                  key={v.id}
                  type="button"
                  onClick={() => {
                    onPick({ product: detail, variant: v });
                    setDetail(null);
                    setQ("");
                    setResults([]);
                  }}
                  className="rounded-md border border-line-strong px-3 py-1.5 text-sm hover:bg-brand-50"
                >
                  {v.name} · {qty(v.stock.available)} available
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
