"use client";

import { clsx } from "clsx";
import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

export interface Column<T> {
  header: string;
  cell: (row: T) => ReactNode;
  align?: "left" | "right";
  className?: string;
  /** Hidden in the mobile card layout. */
  hideOnMobile?: boolean;
}

/**
 * Business table on wide screens; stacked record cards on phones (no horizontal scrolling).
 * The first column becomes the card title.
 */
export function DataTable<T>({
  rows,
  columns,
  rowKey,
  rowHref,
  caption,
}: {
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T) => string | number;
  rowHref?: (row: T) => string;
  caption: string;
}) {
  const [first, ...rest] = columns;
  return (
    <>
      <div className="hidden md:block">
        <table className="w-full text-sm">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr className="border-b border-line bg-canvas/70 text-left text-xs font-semibold uppercase tracking-wide text-muted">
              {columns.map((c) => (
                <th key={c.header} scope="col" className={clsx("px-4 py-2.5 font-semibold", c.align === "right" && "text-right", c.className)}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((row) => (
              <tr key={rowKey(row)} className={clsx("align-middle", rowHref && "hover:bg-brand-50/50")}>
                {columns.map((c, i) => (
                  <td key={c.header} className={clsx("px-4 py-3", c.align === "right" && "text-right tabular", c.className)}>
                    {i === 0 && rowHref ? (
                      <Link href={rowHref(row)} className="font-medium text-brand-700 hover:underline">
                        {c.cell(row)}
                      </Link>
                    ) : (
                      c.cell(row)
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="divide-y divide-line md:hidden" aria-label={caption}>
        {rows.map((row) => (
          <li key={rowKey(row)} className="px-4 py-3">
            <div className="font-medium">
              {rowHref ? (
                <Link href={rowHref(row)} className="text-brand-700 hover:underline">
                  {first.cell(row)}
                </Link>
              ) : (
                first.cell(row)
              )}
            </div>
            <dl className="mt-1.5 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              {rest
                .filter((c) => !c.hideOnMobile)
                .map((c) => (
                  <div key={c.header} className="min-w-0">
                    <dt className="text-xs text-muted">{c.header}</dt>
                    <dd className="truncate tabular">{c.cell(row)}</dd>
                  </div>
                ))}
            </dl>
          </li>
        ))}
      </ul>
    </>
  );
}

export function Pagination({ page, totalPages, totalElements, onPage }: { page: number; totalPages: number; totalElements: number; onPage: (p: number) => void }) {
  if (totalElements === 0) return null;
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm">
      <p className="text-muted">
        Page <span className="font-medium text-ink">{page + 1}</span> of {Math.max(totalPages, 1)} · {totalElements} records
      </p>
      <div className="flex gap-2">
        <button type="button" onClick={() => onPage(page - 1)} disabled={page <= 0} className="inline-flex h-8 items-center gap-1 rounded-lg border border-line-strong px-2.5 font-medium hover:bg-subtle disabled:opacity-50" aria-label="Previous page">
          <ChevronLeft className="size-4" aria-hidden /> <span className="hidden sm:inline">Previous</span>
        </button>
        <button type="button" onClick={() => onPage(page + 1)} disabled={page + 1 >= totalPages} className="inline-flex h-8 items-center gap-1 rounded-lg border border-line-strong px-2.5 font-medium hover:bg-subtle disabled:opacity-50" aria-label="Next page">
          <span className="hidden sm:inline">Next</span> <ChevronRight className="size-4" aria-hidden />
        </button>
      </div>
    </nav>
  );
}
