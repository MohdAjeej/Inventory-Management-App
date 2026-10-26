"use client";

import { clsx } from "clsx";
import { useId, useRef, type KeyboardEvent } from "react";

/** WAI-ARIA tabs with arrow-key navigation. Render the active panel yourself with `panelProps`. */
export function Tabs<K extends string>({ tabs, value, onChange, label }: { tabs: { key: K; label: string }[]; value: K; onChange: (k: K) => void; label: string }) {
  const id = useId();
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const onKey = (e: KeyboardEvent, i: number) => {
    const dir = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0;
    if (!dir) return;
    e.preventDefault();
    const next = (i + dir + tabs.length) % tabs.length;
    refs.current[next]?.focus();
    onChange(tabs[next].key);
  };
  return (
    <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto overflow-y-hidden border-b border-line">
      {tabs.map((t, i) => (
        <button
          key={t.key}
          ref={(el) => {
            refs.current[i] = el;
          }}
          role="tab"
          type="button"
          id={`${id}-${t.key}`}
          aria-selected={t.key === value}
          tabIndex={t.key === value ? 0 : -1}
          onClick={() => onChange(t.key)}
          onKeyDown={(e) => onKey(e, i)}
          className={clsx(
            "-mb-px shrink-0 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors",
            t.key === value ? "border-brand-600 text-brand-700" : "border-transparent text-muted hover:text-ink",
          )}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
