"use client";

import { clsx } from "clsx";
import { useId, type ComponentProps, type ReactNode } from "react";

const control =
  "block w-full rounded-lg border border-line-strong bg-surface px-3 text-sm text-ink placeholder:text-muted/70 shadow-sm transition-colors hover:border-muted/60 focus:border-brand-600 focus:outline-2 focus:outline-offset-0 focus:outline-brand-100 disabled:bg-subtle disabled:text-muted aria-[invalid=true]:border-danger aria-[invalid=true]:focus:outline-danger-bg";

interface FieldProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  required?: boolean;
  className?: string;
  children: (props: { id: string; "aria-invalid"?: boolean; "aria-describedby"?: string; required?: boolean }) => ReactNode;
}

/** Label + control + hint/error, wired up for screen readers. */
export function Field({ label, error, hint, required, className, children }: FieldProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  return (
    <div className={clsx("space-y-1.5", className)}>
      <label htmlFor={id} className="block text-sm font-medium text-ink-2">
        {label}
        {required && <span className="text-danger" aria-hidden> *</span>}
      </label>
      {children({
        id,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": [hintId, errorId].filter(Boolean).join(" ") || undefined,
        required,
      })}
      {hint && !error && (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs font-medium text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export function Input({ className, ...rest }: ComponentProps<"input">) {
  return <input className={clsx(control, "h-10", className)} {...rest} />;
}

export function Select({ className, children, ...rest }: ComponentProps<"select">) {
  return (
    <select className={clsx(control, "h-10 pr-8", className)} {...rest}>
      {children}
    </select>
  );
}

export function Textarea({ className, ...rest }: ComponentProps<"textarea">) {
  return <textarea className={clsx(control, "min-h-24 py-2", className)} {...rest} />;
}

export function Checkbox({ label, description, className, ...rest }: ComponentProps<"input"> & { label: string; description?: string }) {
  const id = useId();
  return (
    <div className={clsx("flex items-start gap-3", className)}>
      <input id={id} type="checkbox" className="mt-0.5 size-4 rounded border-line-strong accent-brand-600" {...rest} />
      <label htmlFor={id} className="text-sm">
        <span className="font-medium text-ink">{label}</span>
        {description && <span className="block text-muted">{description}</span>}
      </label>
    </div>
  );
}
