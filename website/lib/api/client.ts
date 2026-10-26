import type { ApiErrorBody } from "./types";

export type Query = Record<string, string | number | boolean | null | undefined | (string | number)[]>;

export interface RequestOptions {
  query?: Query;
  body?: unknown;
  signal?: AbortSignal;
}

/**
 * The single seam between UI and data. `live` talks to the Spring backend through the
 * same-origin proxy at /api/backend; `demo` serves in-memory sample data (lib/demo).
 */
export interface ApiClient {
  mode: "live" | "demo";
  request<T>(method: "GET" | "POST" | "PUT" | "DELETE", path: string, options?: RequestOptions): Promise<T>;
  /** URL for a CSV/XLSX export, or null when exports are unavailable (demo). */
  exportUrl(path: string, query?: Query): string | null;
  /** Multipart upload (field name "file"), used for product images and the business logo. */
  upload<T>(path: string, file: File): Promise<T>;
}

/** Backend file paths look like /api/files/abc.jpg; serve them through the same-origin proxy. */
export function fileUrl(path: string | undefined | null): string | undefined {
  if (!path) return undefined;
  if (/^(https?:|data:|blob:)/.test(path)) return path;
  return `${BACKEND_PROXY}${path.replace(/^\/api/, "")}`;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(body: ApiErrorBody) {
    super(body.message || "Something went wrong");
    this.name = "ApiError";
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors ?? {};
  }
}

export function toQueryString(query?: Query): string {
  if (!query) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) value.forEach((v) => params.append(key, String(v)));
    else params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export const BACKEND_PROXY = "/api/backend";

/** Sends a request to the proxy; network failures become a typed NETWORK_ERROR. */
async function send(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, { ...init, cache: "no-store" });
  } catch (err) {
    if ((err as Error).name === "AbortError") throw err;
    throw new ApiError({ status: 0, code: "NETWORK_ERROR", message: "Could not reach the server. Check your connection and try again." });
  }
}

/** Shared by request() and upload(): parses JSON, signals an expired session, throws ApiError. */
async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : undefined;
  if (!res.ok) {
    if (res.status === 401 && typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("session-expired"));
    }
    const body = data as ApiErrorBody | undefined;
    throw new ApiError({
      status: res.status,
      code: body?.code ?? "HTTP_" + res.status,
      message: body?.message ?? defaultMessage(res.status),
      fieldErrors: body?.fieldErrors,
    });
  }
  return data as T;
}

export function createLiveClient(): ApiClient {
  return {
    mode: "live",
    async request<T>(method: string, path: string, options: RequestOptions = {}) {
      const res = await send(`${BACKEND_PROXY}${path}${toQueryString(options.query)}`, {
        method,
        headers: options.body !== undefined ? { "Content-Type": "application/json" } : undefined,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
        signal: options.signal,
      });
      return parse<T>(res);
    },
    exportUrl(path, query) {
      return `${BACKEND_PROXY}${path}${toQueryString(query)}`;
    },
    async upload<T>(path: string, file: File) {
      const form = new FormData();
      form.append("file", file);
      return parse<T>(await send(`${BACKEND_PROXY}${path}`, { method: "POST", body: form }));
    },
  };
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function defaultMessage(status: number): string {
  if (status === 401) return "Your session has ended. Please sign in again.";
  if (status === 403) return "You don't have permission to do this.";
  if (status === 404) return "Not found.";
  if (status >= 500) return "The server had a problem. Please try again.";
  return "Request failed.";
}

export function newRequestId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}
