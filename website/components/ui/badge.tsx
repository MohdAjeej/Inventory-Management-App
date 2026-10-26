import { clsx } from "clsx";
import { AlertTriangle, CheckCircle2, Circle, Clock, FileEdit, MinusCircle, XCircle } from "lucide-react";
import type { ReactNode } from "react";

type Tone = "success" | "warning" | "danger" | "info" | "neutral";

const tones: Record<Tone, string> = {
  success: "bg-success-bg text-success",
  warning: "bg-warning-bg text-warning",
  danger: "bg-danger-bg text-danger",
  info: "bg-info-bg text-info",
  neutral: "bg-subtle text-ink-2",
};

export function Badge({ tone = "neutral", icon, children, className }: { tone?: Tone; icon?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <span className={clsx("inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium", tones[tone], className)}>
      {icon}
      {children}
    </span>
  );
}

const icon = "size-3.5";

/** Every backend status, with tone + icon + label so meaning never relies on colour alone. */
const STATUS: Record<string, { tone: Tone; label: string; icon: ReactNode }> = {
  IN_STOCK: { tone: "success", label: "In stock", icon: <CheckCircle2 className={icon} aria-hidden /> },
  LOW_STOCK: { tone: "warning", label: "Low stock", icon: <AlertTriangle className={icon} aria-hidden /> },
  OUT_OF_STOCK: { tone: "danger", label: "Out of stock", icon: <XCircle className={icon} aria-hidden /> },
  PAID: { tone: "success", label: "Paid", icon: <CheckCircle2 className={icon} aria-hidden /> },
  PARTIALLY_PAID: { tone: "warning", label: "Partly paid", icon: <Clock className={icon} aria-hidden /> },
  UNPAID: { tone: "danger", label: "Unpaid", icon: <Circle className={icon} aria-hidden /> },
  COMPLETED: { tone: "success", label: "Completed", icon: <CheckCircle2 className={icon} aria-hidden /> },
  RECEIVED: { tone: "success", label: "Received", icon: <CheckCircle2 className={icon} aria-hidden /> },
  CANCELLED: { tone: "neutral", label: "Cancelled", icon: <MinusCircle className={icon} aria-hidden /> },
  DRAFT: { tone: "neutral", label: "Draft", icon: <FileEdit className={icon} aria-hidden /> },
  PENDING_APPROVAL: { tone: "warning", label: "Pending approval", icon: <Clock className={icon} aria-hidden /> },
  POSTED: { tone: "success", label: "Posted", icon: <CheckCircle2 className={icon} aria-hidden /> },
  REJECTED: { tone: "danger", label: "Rejected", icon: <XCircle className={icon} aria-hidden /> },
  REQUESTED: { tone: "warning", label: "Requested", icon: <Clock className={icon} aria-hidden /> },
  ACCEPTED: { tone: "success", label: "Accepted", icon: <CheckCircle2 className={icon} aria-hidden /> },
  PENDING: { tone: "warning", label: "Pending", icon: <Clock className={icon} aria-hidden /> },
  ACTIVE: { tone: "success", label: "Active", icon: <CheckCircle2 className={icon} aria-hidden /> },
  DEACTIVATED: { tone: "neutral", label: "Deactivated", icon: <MinusCircle className={icon} aria-hidden /> },
  ARCHIVED: { tone: "neutral", label: "Archived", icon: <MinusCircle className={icon} aria-hidden /> },
};

export function StatusChip({ status, className }: { status: string; className?: string }) {
  const s = STATUS[status] ?? { tone: "neutral" as Tone, label: status, icon: null };
  return (
    <Badge tone={s.tone} icon={s.icon} className={className}>
      {s.label}
    </Badge>
  );
}
