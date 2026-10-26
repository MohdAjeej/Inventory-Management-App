import { clsx } from "clsx";
import { AlertCircle, Info, Loader2, RefreshCw, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx("animate-pulse rounded-md bg-subtle", className)} aria-hidden />;
}

export function LoadingBlock({ label = "Loading…", rows = 4 }: { label?: string; rows?: number }) {
  return (
    <div role="status" aria-live="polite" className="space-y-3 p-5">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className={clsx("h-5", i % 2 ? "w-3/4" : "w-full")} />
      ))}
    </div>
  );
}

export function Spinner({ label = "Loading" }: { label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2 text-sm text-muted">
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {label}
    </span>
  );
}

export function EmptyState({ icon: Icon = Info, title, description, action, className }: { icon?: LucideIcon; title: string; description?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={clsx("flex flex-col items-center px-6 py-12 text-center", className)}>
      <div className="mb-3 grid size-11 place-items-center rounded-full bg-brand-50 text-brand-600">
        <Icon className="size-5" aria-hidden />
      </div>
      <p className="font-semibold text-ink">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry, className }: { error: unknown; onRetry?: () => void; className?: string }) {
  const err = error as { message?: string; status?: number; code?: string };
  const forbidden = err?.status === 403;
  return (
    <div role="alert" className={clsx("flex flex-col items-center px-6 py-10 text-center", className)}>
      <div className="mb-3 grid size-11 place-items-center rounded-full bg-danger-bg text-danger">
        <AlertCircle className="size-5" aria-hidden />
      </div>
      <p className="font-semibold text-ink">{forbidden ? "You don't have access to this" : "Couldn't load this"}</p>
      <p className="mt-1 max-w-md text-sm text-muted">
        {forbidden ? "Ask your business admin if you need this permission." : err?.message ?? "Something went wrong."}
      </p>
      {onRetry && !forbidden && (
        <button type="button" onClick={onRetry} className="mt-4 inline-flex h-9 items-center gap-2 rounded-lg border border-line-strong bg-surface px-3 text-sm font-medium hover:bg-subtle">
          <RefreshCw className="size-4" aria-hidden /> Try again
        </button>
      )}
    </div>
  );
}

type NoticeTone = "info" | "warning" | "danger" | "success";
const noticeTones: Record<NoticeTone, string> = {
  info: "border-brand-200 bg-brand-50 text-ink [&>svg]:text-info",
  warning: "border-warning/35 bg-warning-bg text-ink [&>svg]:text-warning",
  danger: "border-danger/35 bg-danger-bg text-ink [&>svg]:text-danger",
  success: "border-success/35 bg-success-bg text-ink [&>svg]:text-success",
};

export function Notice({ tone = "info", title, children, className, icon }: { tone?: NoticeTone; title?: string; children?: ReactNode; className?: string; icon?: ReactNode }) {
  return (
    <div role={tone === "danger" ? "alert" : "status"} className={clsx("flex gap-3 rounded-lg border px-4 py-3 text-sm", noticeTones[tone], className)}>
      {icon ?? <Info className="mt-0.5 size-4 shrink-0" aria-hidden />}
      <div className="min-w-0">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={title ? "mt-0.5" : undefined}>{children}</div>}
      </div>
    </div>
  );
}
