"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type DependencyList, type ReactNode } from "react";
import { createLiveClient, type ApiClient } from "@/lib/api/client";
import { api } from "@/lib/api/endpoints";
import type { Me, Permission, Role } from "@/lib/api/types";
import { createDemoClient, demoMe } from "@/lib/demo/client";
import { can as canFn } from "@/lib/permissions";

interface DashboardCtx {
  mode: "live" | "demo";
  client: ApiClient;
  me: Me;
  /** Prefix a dashboard path: href("/sales") → /dashboard/sales or /dashboard/demo/sales. */
  href: (path: string) => string;
  can: (...p: Permission[]) => boolean;
  setDemoRole: (r: Role) => void;
  setMe: (me: Me) => void;
  currency: string;
}

const Ctx = createContext<DashboardCtx | null>(null);

export function useDashboard(): DashboardCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useDashboard must be used inside DashboardProvider");
  return ctx;
}

export function DashboardProvider({ mode, children, fallback }: { mode: "live" | "demo"; children: ReactNode; fallback: (state: { error?: unknown; retry: () => void }) => ReactNode }) {
  const router = useRouter();
  const [role, setRole] = useState<Role>("ADMIN");
  const roleRef = useRef(role);
  roleRef.current = role;
  const client = useMemo(() => (mode === "demo" ? createDemoClient(() => roleRef.current) : createLiveClient()), [mode]);
  const [me, setMe] = useState<Me | null>(mode === "demo" ? demoMe("ADMIN") : null);
  const [error, setError] = useState<unknown>();
  const [currency, setCurrency] = useState("INR");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (mode === "demo") return;
    let cancelled = false;
    setError(undefined);
    api
      .me(client)
      .then((m) => !cancelled && setMe(m))
      .catch((e) => {
        if (cancelled) return;
        if (e?.status === 401) router.replace(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`);
        else setError(e);
      });
    return () => {
      cancelled = true;
    };
  }, [mode, client, router, attempt]);

  useEffect(() => {
    if (!me) return;
    api.business.settings(client).then((s) => s.currencyCode && setCurrency(s.currencyCode)).catch(() => {});
  }, [client, me?.businessId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (mode !== "live") return;
    const onExpired = () => router.replace(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}&expired=1`);
    window.addEventListener("session-expired", onExpired);
    return () => window.removeEventListener("session-expired", onExpired);
  }, [mode, router]);

  const base = mode === "demo" ? "/dashboard/demo" : "/dashboard";
  const value = useMemo<DashboardCtx | null>(
    () =>
      me
        ? {
            mode,
            client,
            me,
            href: (p: string) => `${base}${p}`,
            can: (...p: Permission[]) => canFn(me, ...p),
            setDemoRole: (r: Role) => {
              setRole(r);
              setMe(demoMe(r));
            },
            setMe,
            currency,
          }
        : null,
    [me, mode, client, base, currency],
  );

  if (!value) return <>{fallback({ error, retry: () => setAttempt((a) => a + 1) })}</>;
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

/** Minimal fetch-state hook: data / error / loading / reload, cancelling stale requests. */
export function useApi<T>(fetcher: () => Promise<T>, deps: DependencyList) {
  const [state, setState] = useState<{ data?: T; error?: unknown; loading: boolean }>({ loading: true });
  const [tick, setTick] = useState(0);
  const fetchRef = useRef(fetcher);
  fetchRef.current = fetcher;

  useEffect(() => {
    let active = true;
    setState((s) => ({ data: s.data, loading: true }));
    fetchRef
      .current()
      .then((data) => active && setState({ data, loading: false }))
      .catch((error) => active && setState({ error, loading: false }));
    return () => {
      active = false;
    };
  }, [...deps, tick]); // eslint-disable-line react-hooks/exhaustive-deps

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { ...state, reload, setData: (data: T) => setState({ data, loading: false }) };
}

/** Debounces a value (search boxes). */
export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}
