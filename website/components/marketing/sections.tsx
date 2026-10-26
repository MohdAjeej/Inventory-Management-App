import {
  ArrowRight,
  BarChart3,
  Boxes,
  Building2,
  FileText,
  Network,
  Package,
  ShieldCheck,
  ShoppingCart,
  Store,
  Truck,
  Users,
  Warehouse,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { Logo } from "@/components/logo";
import { ButtonLink } from "@/components/ui/button";
import { marketingNav, siteConfig } from "@/lib/site";

export function Container({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`mx-auto max-w-6xl px-4 sm:px-6 ${className}`}>{children}</div>;
}

export function SectionHeading({ eyebrow, title, description, center }: { eyebrow?: string; title: string; description?: string; center?: boolean }) {
  return (
    <div className={center ? "mx-auto max-w-2xl text-center" : "max-w-2xl"}>
      {eyebrow && <p className="text-sm font-semibold text-accent-700">{eyebrow}</p>}
      <h2 className="mt-2 text-3xl font-semibold tracking-tight text-navy-950 sm:text-[2.1rem]">{title}</h2>
      {description && <p className="mt-3 text-base leading-relaxed text-muted">{description}</p>}
    </div>
  );
}

export function PageIntro({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return (
    <section className="border-b border-line bg-white">
      <Container className="py-14 sm:py-20">
        <p className="text-sm font-semibold text-accent-700">{eyebrow}</p>
        <h1 className="mt-2 max-w-3xl text-4xl font-semibold tracking-tight text-navy-950 sm:text-5xl">{title}</h1>
        <p className="mt-4 max-w-2xl text-lg leading-relaxed text-muted">{description}</p>
      </Container>
    </section>
  );
}

/** Capabilities confirmed in README.md / docs/API.md. Keep this list in sync with the product. */
export const FEATURES: { icon: LucideIcon; title: string; text: string; points: string[] }[] = [
  {
    icon: Package,
    title: "Product catalogue",
    text: "Categories and subcategories, custom fields per category, units with decimal precision, and variants such as size or colour.",
    points: ["Search by name, brand, SKU or barcode", "Separate purchase price and selling price", "Images, minimum stock and archiving"],
  },
  {
    icon: Boxes,
    title: "Stock tracking",
    text: "Every change is a permanent movement record: opening stock, add, reduce, adjust with a reason, damaged and reserved stock.",
    points: ["Stock never goes negative unless you allow it", "Optional batches, expiry dates and serial numbers", "Stock counts that reconcile into adjustments"],
  },
  {
    icon: ShoppingCart,
    title: "Sales and purchases",
    text: "Multi-product sales and purchases with discounts, tax and payments. Saving a sale deducts stock in one step.",
    points: ["Paid, partly paid and unpaid tracking", "Partial returns and cancellations", "Weighted-average cost from purchases"],
  },
  {
    icon: Users,
    title: "Customers and suppliers",
    text: "Keep your customer and supplier lists alongside every sale and purchase, and save new ones as you record transactions.",
    points: ["Contact details and tax IDs", "Search and archive", "Linked to sales and purchases"],
  },
  {
    icon: FileText,
    title: "B2B invoices",
    text: "Connected businesses invoice each other. When the buyer approves, the seller's sale and the buyer's purchase are recorded together.",
    points: ["Sending reserves the seller's stock", "Buyer reviews where each line goes", "Returns against approved invoices"],
  },
  {
    icon: ShieldCheck,
    title: "Employee permissions",
    text: "Admin, Inventory Manager and Staff roles with sensible defaults, plus per-person overrides. Changes apply immediately.",
    points: ["Invite employees with a code", "Costs hidden from roles that shouldn't see them", "Deactivate access instantly"],
  },
  {
    icon: BarChart3,
    title: "Reports and analytics",
    text: "Net sales, gross profit, trends, product rankings, category analysis and stock movement summaries.",
    points: ["Compared with the previous period", "Fast- and slow-moving products", "CSV and Excel exports"],
  },
  {
    icon: Warehouse,
    title: "Warehouses and branches",
    text: "Track stock separately in each warehouse or branch, and move it between them with paired transfer records.",
    points: ["Default warehouse per business", "Storage locations per warehouse", "Stock by warehouse on every product"],
  },
];

export function FeatureGrid({ limit }: { limit?: number }) {
  const items = limit ? FEATURES.slice(0, limit) : FEATURES;
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {items.map((f) => (
        <li key={f.title} className="rounded-xl border border-line bg-white p-5 shadow-[var(--shadow-card)]">
          <span className="grid size-10 place-items-center rounded-lg bg-brand-50 text-brand-600">
            <f.icon className="size-5" aria-hidden />
          </span>
          <h3 className="mt-4 font-semibold text-navy-950">{f.title}</h3>
          <p className="mt-1.5 text-sm leading-relaxed text-muted">{f.text}</p>
        </li>
      ))}
    </ul>
  );
}

export const USE_CASES: { icon: LucideIcon; title: string; text: string; how: string[] }[] = [
  {
    icon: Building2,
    title: "Wholesalers",
    text: "Sell in bulk to retailers and keep dealer costs and selling prices apart.",
    how: ["Send B2B invoices to connected retailers", "Reserve stock while invoices await approval", "See margins per product"],
  },
  {
    icon: Truck,
    title: "Distributors",
    text: "Run several warehouses or branches and keep stock accurate across all of them.",
    how: ["Transfer stock between warehouses", "Track batches and expiry where needed", "Spot fast and slow movers"],
  },
  {
    icon: Store,
    title: "Retailers",
    text: "Record counter sales quickly and receive supplier invoices without re-typing them.",
    how: ["Approve supplier invoices into your stock", "Low-stock alerts before you run out", "Track customer payments and dues"],
  },
  {
    icon: Network,
    title: "Multi-business operators",
    text: "Belong to several businesses with one sign-in, and switch between them.",
    how: ["Each business's data is fully separate", "A different role in each business", "Businesses can trade with each other"],
  },
];

export function UseCaseGrid() {
  return (
    <ul className="grid gap-4 md:grid-cols-2">
      {USE_CASES.map((u) => (
        <li key={u.title} className="flex gap-4 rounded-xl border border-line bg-white p-5">
          <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-accent-50 text-accent-700">
            <u.icon className="size-5" aria-hidden />
          </span>
          <div>
            <h3 className="font-semibold text-navy-950">{u.title}</h3>
            <p className="mt-1 text-sm text-muted">{u.text}</p>
            <ul className="mt-3 space-y-1 text-sm text-ink-2">
              {u.how.map((h) => (
                <li key={h} className="flex gap-2"><span className="mt-2 size-1.5 shrink-0 rounded-full bg-accent-600" aria-hidden />{h}</li>
              ))}
            </ul>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function ClosingCta() {
  return (
    <section className="bg-navy-950">
      <Container className="flex flex-col items-start justify-between gap-6 py-14 md:flex-row md:items-center">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">Bring stock, sales and B2B invoicing together</h2>
          <p className="mt-2 max-w-xl text-brand-100/80">Create a workspace for your business, or look around the demo dashboard first. The demo uses sample data.</p>
        </div>
        <div className="flex flex-wrap gap-3">
          <ButtonLink href="/signup" variant="inverse" size="lg">Create workspace <ArrowRight className="size-4" aria-hidden /></ButtonLink>
          <ButtonLink href="/dashboard/demo" size="lg" className="bg-white/10 hover:bg-white/15">View demo</ButtonLink>
        </div>
      </Container>
    </section>
  );
}

export function SiteFooter() {
  return (
    <footer className="border-t border-line bg-white">
      <Container className="grid gap-10 py-12 md:grid-cols-4">
        <div className="md:col-span-2">
          <Logo />
          <p className="mt-3 max-w-sm text-sm text-muted">{siteConfig.description}</p>
        </div>
        <nav aria-label="Footer">
          <p className="text-sm font-semibold text-navy-950">Product</p>
          <ul className="mt-3 space-y-2 text-sm">
            {marketingNav.map((n) => <li key={n.href}><Link href={n.href} className="text-muted hover:text-ink">{n.label}</Link></li>)}
            <li><Link href="/dashboard/demo" className="text-muted hover:text-ink">Demo dashboard</Link></li>
          </ul>
        </nav>
        <div>
          <p className="text-sm font-semibold text-navy-950">Contact</p>
          <ul className="mt-3 space-y-2 text-sm text-muted">
            <li>{siteConfig.contact.email}</li>
            <li>{siteConfig.contact.phone}</li>
            <li className="text-xs">{siteConfig.contact.note}</li>
          </ul>
        </div>
      </Container>
      <div className="border-t border-line">
        <Container className="flex flex-col justify-between gap-2 py-5 text-xs text-muted sm:flex-row">
          <p>© {new Date().getFullYear()} {siteConfig.name}. All rights reserved.</p>
          <p>Privacy policy and terms: to be published before launch.</p>
        </Container>
      </div>
    </footer>
  );
}
