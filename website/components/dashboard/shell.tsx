"use client";

import { clsx } from "clsx";
import {
  BarChart3,
  Bell,
  ClipboardCheck,
  DatabaseBackup,
  Link2,
  Network,
  Palette,
  Boxes,
  ChevronDown,
  CircleUser,
  Factory,
  FileText,
  FlaskConical,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  Settings,
  ShoppingCart,
  Tags,
  Truck,
  UserCog,
  Users,
  Warehouse,
  X,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Logo } from "@/components/logo";
import { api } from "@/lib/api/endpoints";
import type { Role } from "@/lib/api/types";
import { initials } from "@/lib/format";
import { ROLE_LABELS } from "@/lib/permissions";
import { NAV } from "@/lib/routes";
import { useDashboard } from "./context";

const ICONS: Record<string, LucideIcon> = {
  LayoutDashboard, Package, Tags, Boxes, Warehouse, ShoppingCart, Truck, Users, Factory, FileText, BarChart3, UserCog, Settings, CircleUser,
  ClipboardCheck, Network, Link2, DatabaseBackup, Palette, Bell,
};

function useNav() {
  const { can } = useDashboard();
  return NAV.map((g) => ({ ...g, items: g.items.filter((i) => i.any.length === 0 || can(...i.any)) })).filter((g) => g.items.length);
}

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const { href } = useDashboard();
  const pathname = usePathname();
  const groups = useNav();
  return (
    <nav aria-label="Dashboard" className="space-y-6">
      {groups.map((g) => (
        <div key={g.label}>
          <p className="px-3 text-[11px] font-semibold uppercase tracking-wider text-white/50">{g.label}</p>
          <ul className="mt-2 space-y-0.5">
            {g.items.map((item) => {
              const target = href(item.path);
              const active = item.path === "" ? pathname === target : pathname === target || pathname.startsWith(target + "/");
              const Icon = ICONS[item.icon] ?? Package;
              return (
                <li key={item.path}>
                  <Link
                    href={target}
                    onClick={onNavigate}
                    aria-current={active ? "page" : undefined}
                    className={clsx(
                      "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-white",
                      active ? "bg-white/12 text-white" : "text-white/75 hover:bg-white/6 hover:text-white",
                    )}
                  >
                    <Icon className="size-4 shrink-0" aria-hidden />
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function BusinessSwitcher() {
  const { me, mode } = useDashboard();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  if (me.memberships.length <= 1) {
    return (
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-ink">{me.businessName}</p>
        <p className="text-xs text-muted">{ROLE_LABELS[me.role]}</p>
      </div>
    );
  }
  return (
    <div className="min-w-0">
      <label htmlFor="business-switcher" className="sr-only">
        Business
      </label>
      <select
        id="business-switcher"
        value={me.businessId}
        disabled={busy || mode === "demo"}
        onChange={async (e) => {
          setBusy(true);
          setError(undefined);
          try {
            const res = await fetch("/api/auth/switch-business", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ businessId: Number(e.target.value) }),
            });
            if (res.ok) {
              // Full reload so no data from the previous business stays in memory.
              window.location.assign("/dashboard");
              return;
            }
            setError(res.status === 401 ? "Your session has ended. Please sign in again." : "Couldn't switch business");
          } catch {
            setError("Couldn't reach the server. Check your connection.");
          }
          setBusy(false);
        }}
        className="max-w-56 truncate rounded-md border border-transparent bg-transparent py-1 pr-6 text-sm font-semibold text-ink hover:border-line"
      >
        {me.memberships.map((m) => (
          <option key={m.businessId} value={m.businessId}>
            {m.businessName} · {ROLE_LABELS[m.role]}
          </option>
        ))}
      </select>
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  );
}

function UserMenu() {
  const { me, mode, href } = useDashboard();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const close = (e: MouseEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", esc);
    };
  }, []);
  return (
    <div className="relative" ref={ref}>
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="menu" className="flex items-center gap-2 rounded-lg p-1 hover:bg-subtle">
        <span className="grid size-8 place-items-center rounded-full bg-brand-600 text-xs font-semibold text-white" aria-hidden>
          {initials(me.fullName)}
        </span>
        <span className="hidden text-sm font-medium lg:inline">{me.fullName}</span>
        <ChevronDown className="size-4 text-muted" aria-hidden />
        <span className="sr-only">Account menu</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-40 mt-2 w-56 rounded-lg border border-line bg-surface p-1 shadow-[var(--shadow-pop)]">
          <div className="border-b border-line px-3 py-2">
            <p className="truncate text-sm font-medium">{me.fullName}</p>
            <p className="truncate text-xs text-muted">{me.email}</p>
          </div>
          <Link role="menuitem" href={href("/profile")} onClick={() => setOpen(false)} className="flex items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-subtle">
            <CircleUser className="size-4" aria-hidden /> Profile
          </Link>
          {mode === "live" ? (
            <button
              role="menuitem"
              type="button"
              onClick={async () => {
                await fetch("/api/auth/logout", { method: "POST" });
                window.location.assign("/login");
              }}
              className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-danger hover:bg-danger-bg"
            >
              <LogOut className="size-4" aria-hidden /> Sign out
            </button>
          ) : (
            <Link role="menuitem" href="/" className="flex items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-subtle">
              <LogOut className="size-4" aria-hidden /> Exit demo
            </Link>
          )}
        </div>
      )}
    </div>
  );
}

function NotificationBell() {
  const { client, href } = useDashboard();
  const [count, setCount] = useState<number>();
  const pathname = usePathname();
  useEffect(() => {
    let active = true;
    const load = () =>
      api.notifications
        .list(client, { unreadOnly: true, size: 1 })
        .then((p) => active && setCount(p.totalElements))
        .catch(() => {});
    load();
    const t = setInterval(load, 60_000);
    return () => {
      active = false;
      clearInterval(t);
    };
  }, [client, pathname]);
  return (
    <Link href={href("/notifications")} className="relative rounded-lg p-2 text-ink-2 hover:bg-subtle" aria-label={count ? `Notifications, ${count} unread` : "Notifications"}>
      <Bell className="size-5" aria-hidden />
      {!!count && (
        <span className="absolute right-1 top-1 grid min-w-4 place-items-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-4 text-white" aria-hidden>
          {count > 9 ? "9+" : count}
        </span>
      )}
    </Link>
  );
}

function DemoBanner() {
  const { mode, me, setDemoRole } = useDashboard();
  if (mode !== "demo") return null;
  return (
    <div className="border-b border-warning/30 bg-warning-bg px-4 py-2 text-sm text-ink">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <p className="flex items-center gap-2">
          <FlaskConical className="size-4 shrink-0 text-warning" aria-hidden />
          <span>
            <strong>Demo mode — sample data, not a real business.</strong> Changes are kept in this tab only and reset on reload.
          </span>
        </p>
        <div className="flex items-center gap-2">
          <label htmlFor="demo-role" className="text-xs font-medium">
            View as
          </label>
          <select id="demo-role" value={me.role} onChange={(e) => setDemoRole(e.target.value as Role)} className="h-8 rounded-md border border-warning/40 bg-surface px-2 text-sm text-ink">
            {(Object.keys(ROLE_LABELS) as Role[]).map((r) => (
              <option key={r} value={r}>
                {ROLE_LABELS[r]}
              </option>
            ))}
          </select>
        </div>
      </div>
    </div>
  );
}

export function DashboardShell({ children }: { children: ReactNode }) {
  const { href, me } = useDashboard();
  const [drawer, setDrawer] = useState(false);
  const pathname = usePathname();
  useEffect(() => setDrawer(false), [pathname]);

  return (
    <div className="app-theme min-h-dvh bg-canvas text-ink">
      <a href="#main" className="sr-only z-50 rounded bg-white px-3 py-2 focus:not-sr-only focus:fixed focus:left-3 focus:top-3">
        Skip to content
      </a>
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 hidden w-64 flex-col bg-navy-950 lg:flex">
        <div className="px-5 py-5">
          <Link href={href("")} className="rounded focus-visible:outline-white">
            <Logo inverse />
          </Link>
        </div>
        <div className="flex-1 overflow-y-auto px-3 pb-6">
          <NavLinks />
        </div>
      </aside>

      {/* Mobile drawer */}
      {drawer && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-navy-950/50" onClick={() => setDrawer(false)} aria-hidden />
          <div className="absolute inset-y-0 left-0 flex w-72 max-w-[85%] flex-col bg-navy-950">
            <div className="flex items-center justify-between px-5 py-4">
              <Logo inverse />
              <button type="button" onClick={() => setDrawer(false)} className="rounded-md p-1 text-white hover:bg-white/10" aria-label="Close navigation">
                <X className="size-5" aria-hidden />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-3 pb-6">
              <NavLinks onNavigate={() => setDrawer(false)} />
            </div>
          </div>
        </div>
      )}

      <div className="lg:pl-64">
        <DemoBanner />
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-line bg-surface/95 px-4 backdrop-blur sm:px-6">
          <button type="button" onClick={() => setDrawer(true)} className="-ml-1 rounded-md p-2 text-ink-2 hover:bg-subtle lg:hidden" aria-label="Open navigation">
            <Menu className="size-5" aria-hidden />
          </button>
          <div className="min-w-0 flex-1">
            <BusinessSwitcher />
          </div>
          <NotificationBell />
          <UserMenu />
        </header>
        <main id="main" key={me.role} className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:py-8">
          {children}
        </main>
      </div>
    </div>
  );
}
