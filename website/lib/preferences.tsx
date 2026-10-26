"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";

/**
 * Appearance & language, mirroring the Android app's Appearance screen (AppPreferences):
 * theme System / Light / Dark and seven languages. Stored per browser in localStorage.
 *
 * Translation works like the app's I18n.tr(): English UI text is looked up in dictionaries generated
 * from the app's own string resources (scripts/build-i18n.mjs → public/i18n/<lang>.json). Texts built
 * from a template ("Only 5 in stock") are matched against "Only %1$s in stock". Parts separated by
 * " · " are translated piece by piece. Anything unknown — product names, server messages — is left
 * unchanged.
 */

export type ThemeMode = "system" | "light" | "dark";
export const LANGUAGES = [
  { tag: "en", name: "English" },
  { tag: "hi", name: "हिन्दी" },
  { tag: "bn", name: "বাংলা" },
  { tag: "ta", name: "தமிழ்" },
  { tag: "mr", name: "मराठी" },
  { tag: "ar", name: "العربية" },
  { tag: "ur", name: "اردو" },
] as const;
export type LanguageTag = (typeof LANGUAGES)[number]["tag"];
const RTL = new Set(["ar", "ur"]);
const THEME_KEY = "ip-theme";
const LANG_KEY = "ip-lang";

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* private mode: the preference just isn't remembered */
  }
}

/** Inline script for <head>: applies the saved theme before first paint (no flash). */
export const themeBootScript = `(function(){try{var m=localStorage.getItem("${THEME_KEY}")||"system";var d=m==="dark"||(m==="system"&&matchMedia("(prefers-color-scheme: dark)").matches);document.documentElement.dataset.themeResolved=d?"dark":"light";}catch(e){}})();`;

// ------------------------------------------------------------------ translator

interface Catalog {
  exact: Map<string, string>;
  templates: { re: RegExp; out: string }[];
}

const catalogs = new Map<string, Promise<Catalog>>();

function loadCatalog(lang: string): Promise<Catalog> {
  let p = catalogs.get(lang);
  if (!p) {
    p = fetch(`/i18n/${lang}.json`)
      .then((r) => (r.ok ? r.json() : { exact: {}, templates: [] }))
      .then((data: { exact: Record<string, string>; templates: [string, string][] }) => ({
        exact: new Map(Object.entries(data.exact)),
        templates: data.templates
          .map(([en, out]) => {
            const parts = en.split(/%\d+\$[sd]/);
            if (parts.join("").trim().length < 3) return null; // too generic to match safely
            const pattern = en.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/%(\d+)\\\$[sd]/g, "(.+?)");
            return { re: new RegExp(`^${pattern}$`), out };
          })
          .filter((x): x is { re: RegExp; out: string } => x !== null),
      }))
      .catch(() => ({ exact: new Map(), templates: [] }));
    catalogs.set(lang, p);
  }
  return p;
}

function translateText(text: string, c: Catalog): string | null {
  const trimmed = text.trim();
  if (!trimmed || !/[A-Za-z]/.test(trimmed)) return null;
  const lead = text.slice(0, text.indexOf(trimmed));
  const tail = text.slice(text.indexOf(trimmed) + trimmed.length);
  const one = (s: string): string | null => {
    const hit = c.exact.get(s) ?? c.exact.get(s.replace(/[.:]$/, ""));
    if (hit) return s.endsWith(":") && !hit.endsWith(":") ? hit + ":" : hit;
    for (const t of c.templates) {
      const m = s.match(t.re);
      if (m) return t.out.replace(/%(\d+)\$[sd]/g, (_, n) => m[Number(n)] ?? "");
    }
    return null;
  };
  let result = one(trimmed);
  if (result === null && trimmed.includes(" · ")) {
    const parts = trimmed.split(" · ");
    const translated = parts.map((p) => one(p) ?? p);
    if (translated.some((p, i) => p !== parts[i])) result = translated.join(" · ");
  }
  return result === null ? null : lead + result + tail;
}

const SKIP = new Set(["SCRIPT", "STYLE", "TEXTAREA", "CODE", "PRE", "NOSCRIPT"]);
const ATTRS = ["placeholder", "aria-label", "title"] as const;

/** Translates text nodes and a few attributes under `root`, and keeps doing so as React updates the DOM. */
function startTranslator(root: HTMLElement, c: Catalog): () => void {
  const english = new WeakMap<Node, string>(); // original text React wrote
  const written = new WeakMap<Node, string>(); // what we wrote over it
  const attrEnglish = new WeakMap<Element, Record<string, string>>();
  let busy = false;

  const skip = (el: Element | null): boolean => {
    for (let e = el; e && e !== root.parentElement; e = e.parentElement) {
      if (SKIP.has(e.tagName) || e.getAttribute("translate") === "no") return true;
    }
    return false;
  };
  const doText = (node: Text) => {
    if (skip(node.parentElement)) return;
    const current = node.nodeValue ?? "";
    if (written.get(node) === current) return;
    english.set(node, current);
    const t = translateText(current, c);
    if (t !== null && t !== current) {
      written.set(node, t);
      node.nodeValue = t;
    }
  };
  const doAttrs = (el: Element) => {
    if (skip(el)) return;
    const store = attrEnglish.get(el) ?? {};
    for (const a of ATTRS) {
      const v = el.getAttribute(a);
      if (!v) continue;
      if (store[`w:${a}`] === v) continue;
      store[a] = v;
      const t = translateText(v, c);
      if (t !== null && t !== v) {
        store[`w:${a}`] = t;
        el.setAttribute(a, t);
      }
    }
    attrEnglish.set(el, store);
  };
  const walk = (n: Node) => {
    if (n.nodeType === Node.TEXT_NODE) return doText(n as Text);
    if (n.nodeType !== Node.ELEMENT_NODE) return;
    const el = n as Element;
    if (SKIP.has(el.tagName)) return;
    doAttrs(el);
    const tw = document.createTreeWalker(el, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
    for (let cur = tw.nextNode(); cur; cur = tw.nextNode()) {
      if (cur.nodeType === Node.TEXT_NODE) doText(cur as Text);
      else doAttrs(cur as Element);
    }
  };

  walk(root);
  const obs = new MutationObserver((records) => {
    if (busy) return;
    busy = true;
    try {
      for (const r of records) {
        if (r.type === "characterData") doText(r.target as Text);
        else if (r.type === "attributes") doAttrs(r.target as Element);
        else r.addedNodes.forEach(walk);
      }
    } finally {
      busy = false;
    }
  });
  obs.observe(root, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: [...ATTRS] });

  return () => {
    obs.disconnect();
    // Restore English so switching back doesn't need a reload.
    const tw = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
    for (let cur = tw.nextNode(); cur; cur = tw.nextNode()) {
      if (cur.nodeType === Node.TEXT_NODE) {
        const en = english.get(cur);
        if (en !== undefined && written.get(cur) === cur.nodeValue) cur.nodeValue = en;
      } else {
        const store = attrEnglish.get(cur as Element);
        if (store) for (const a of ATTRS) if (store[a] && store[`w:${a}`] === (cur as Element).getAttribute(a)) (cur as Element).setAttribute(a, store[a]);
      }
    }
  };
}

// ------------------------------------------------------------------ provider

interface Prefs {
  theme: ThemeMode;
  resolvedTheme: "light" | "dark";
  setTheme: (t: ThemeMode) => void;
  language: LanguageTag;
  setLanguage: (l: LanguageTag) => void;
}

const PrefsContext = createContext<Prefs>({ theme: "system", resolvedTheme: "light", setTheme: () => {}, language: "en", setLanguage: () => {} });
export const usePreferences = () => useContext(PrefsContext);

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>("system");
  const [systemDark, setSystemDark] = useState(false);
  const [language, setLanguageState] = useState<LanguageTag>("en");

  useEffect(() => {
    const t = read(THEME_KEY);
    if (t === "light" || t === "dark" || t === "system") setThemeState(t);
    const l = read(LANG_KEY);
    if (l && LANGUAGES.some((x) => x.tag === l)) setLanguageState(l as LanguageTag);
    const mq = matchMedia("(prefers-color-scheme: dark)");
    setSystemDark(mq.matches);
    const on = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);

  const resolvedTheme = theme === "dark" || (theme === "system" && systemDark) ? "dark" : "light";
  useEffect(() => {
    document.documentElement.dataset.themeResolved = resolvedTheme;
  }, [resolvedTheme]);

  useEffect(() => {
    const html = document.documentElement;
    html.lang = language;
    html.dir = RTL.has(language) ? "rtl" : "ltr";
    if (language === "en") return;
    let stop: (() => void) | undefined;
    let cancelled = false;
    loadCatalog(language).then((c) => {
      if (!cancelled) stop = startTranslator(document.body, c);
    });
    return () => {
      cancelled = true;
      stop?.();
    };
  }, [language]);

  const setTheme = useCallback((t: ThemeMode) => {
    write(THEME_KEY, t);
    setThemeState(t);
  }, []);
  const setLanguage = useCallback((l: LanguageTag) => {
    write(LANG_KEY, l);
    setLanguageState(l);
  }, []);

  return <PrefsContext.Provider value={{ theme, resolvedTheme, setTheme, language, setLanguage }}>{children}</PrefsContext.Provider>;
}
