"use client";

import { Menu, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Logo } from "@/components/logo";
import { ButtonLink } from "@/components/ui/button";
import { marketingNav } from "@/lib/site";

export function SiteHeader() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  useEffect(() => setOpen(false), [pathname]);
  return (
    <header className="sticky top-0 z-40 border-b border-line/80 bg-white/90 backdrop-blur">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
        <Link href="/" className="rounded" aria-label="Inventory Platform home">
          <Logo />
        </Link>
        <nav aria-label="Main" className="hidden flex-1 md:block">
          <ul className="flex items-center gap-1">
            {marketingNav.map((n) => (
              <li key={n.href}>
                <Link href={n.href} aria-current={pathname === n.href ? "page" : undefined} className="rounded-md px-3 py-2 text-sm font-medium text-ink-2 hover:bg-subtle hover:text-ink aria-[current=page]:text-brand-700">
                  {n.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <div className="ml-auto hidden items-center gap-2 md:flex">
          <ButtonLink href="/login" variant="ghost" size="sm">Sign in</ButtonLink>
          <ButtonLink href="/signup" size="sm">Create workspace</ButtonLink>
        </div>
        <button type="button" className="ml-auto rounded-md p-2 text-ink-2 hover:bg-subtle md:hidden" aria-expanded={open} aria-controls="mobile-nav" aria-label={open ? "Close menu" : "Open menu"} onClick={() => setOpen((o) => !o)}>
          {open ? <X className="size-5" aria-hidden /> : <Menu className="size-5" aria-hidden />}
        </button>
      </div>
      {open && (
        <nav id="mobile-nav" aria-label="Main" className="border-t border-line bg-white px-4 pb-4 md:hidden">
          <ul className="space-y-1 py-2">
            {marketingNav.map((n) => (
              <li key={n.href}>
                <Link href={n.href} className="block rounded-md px-3 py-2.5 font-medium text-ink-2 hover:bg-subtle">{n.label}</Link>
              </li>
            ))}
          </ul>
          <div className="grid grid-cols-2 gap-2">
            <ButtonLink href="/login" variant="secondary">Sign in</ButtonLink>
            <ButtonLink href="/signup">Create workspace</ButtonLink>
          </div>
        </nav>
      )}
    </header>
  );
}
