import { clsx } from "clsx";
import type { ComponentProps, ReactNode } from "react";

export function Card({ className, ...rest }: ComponentProps<"section">) {
  return <section className={clsx("rounded-xl border border-line bg-surface shadow-[var(--shadow-card)]", className)} {...rest} />;
}

export function CardHeader({ title, description, action, className, id }: { title: ReactNode; description?: ReactNode; action?: ReactNode; className?: string; id?: string }) {
  return (
    <div className={clsx("flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4", className)}>
      <div className="min-w-0">
        <h2 id={id} className="text-base font-semibold text-ink">
          {title}
        </h2>
        {description && <p className="mt-0.5 text-sm text-muted">{description}</p>}
      </div>
      {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
    </div>
  );
}

export function CardBody({ className, ...rest }: ComponentProps<"div">) {
  return <div className={clsx("p-5", className)} {...rest} />;
}

/** Key/value rows for detail screens. */
export function DescriptionList({ items, className }: { items: { label: string; value: ReactNode }[]; className?: string }) {
  return (
    <dl className={clsx("grid gap-x-6 gap-y-3 sm:grid-cols-2", className)}>
      {items.map((it) => (
        <div key={it.label} className="min-w-0">
          <dt className="text-xs font-medium uppercase tracking-wide text-muted">{it.label}</dt>
          <dd className="mt-0.5 break-words text-sm text-ink">{it.value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}
