import { siteConfig } from "@/lib/site";

/** Neutral placeholder mark (stacked boxes) until a final brand logo is chosen. */
export function LogoMark({ className = "size-8" }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden focusable="false">
      <rect width="32" height="32" rx="8" fill="#1f5fad" />
      <path d="M9 13.5 16 10l7 3.5-7 3.5-7-3.5Z" fill="#fff" />
      <path d="M9 17l7 3.5 7-3.5" stroke="#cdede8" strokeWidth="2" fill="none" strokeLinejoin="round" />
      <path d="M9 20.5 16 24l7-3.5" stroke="#fff" strokeOpacity=".7" strokeWidth="2" fill="none" strokeLinejoin="round" />
    </svg>
  );
}

export function Logo({ inverse = false }: { inverse?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <LogoMark />
      <span className={`text-[15px] font-semibold tracking-tight ${inverse ? "text-white" : "text-navy-950"}`}>{siteConfig.name}</span>
    </span>
  );
}
