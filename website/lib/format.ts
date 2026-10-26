const moneyFormatters = new Map<string, Intl.NumberFormat>();

/** Formats money in the business currency (BusinessSettings.currencyCode, default INR). */
export function money(value: number | null | undefined, currency = "INR", opts: { compact?: boolean } = {}): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  const key = `${currency}-${opts.compact ? "c" : "f"}`;
  let fmt = moneyFormatters.get(key);
  if (!fmt) {
    try {
      fmt = new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency,
        maximumFractionDigits: opts.compact ? 1 : 2,
        notation: opts.compact ? "compact" : "standard",
      });
    } catch {
      fmt = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 });
    }
    moneyFormatters.set(key, fmt);
  }
  return fmt.format(value);
}

/** Quantities are decimals; each unit defines its precision. */
export function qty(value: number | null | undefined, decimals = 3): string {
  if (value === null || value === undefined) return "—";
  return new Intl.NumberFormat("en-IN", { maximumFractionDigits: decimals }).format(value);
}

export function percent(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined) return "—";
  return `${value.toFixed(digits)}%`;
}

export function signedPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) return "Insufficient data";
  return `${value > 0 ? "+" : ""}${value.toFixed(1)}%`;
}

export function date(value: string | null | undefined): string {
  if (!value) return "—";
  const d = value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value);
  return d.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return "—";
  return new Date(value).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function relativeTime(value: string): string {
  const diff = Date.now() - new Date(value).getTime();
  const mins = Math.round(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days} d ago`;
  return date(value);
}

export function humanize(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w))
    .join(" ");
}

/** YYYY-MM-DD for `n` days before today (local time). */
export function isoDaysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toLocaleDateString("en-CA");
}

export function today(): string {
  return new Date().toLocaleDateString("en-CA");
}

export function firstName(fullName: string | undefined): string {
  return (fullName ?? "").trim().split(/\s+/)[0] ?? "";
}

export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p.charAt(0).toUpperCase())
    .join("");
}
