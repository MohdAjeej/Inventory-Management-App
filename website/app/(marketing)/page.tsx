import { ArrowRight, Boxes, CheckCircle2, Layers, ShieldCheck, Smartphone } from "lucide-react";
import type { Metadata } from "next";
import { DashboardPreview } from "@/components/marketing/dashboard-preview";
import { ClosingCta, Container, FeatureGrid, SectionHeading, UseCaseGrid } from "@/components/marketing/sections";
import { ButtonLink } from "@/components/ui/button";

export const metadata: Metadata = {
  title: { absolute: "Inventory Platform — Inventory and B2B operations in one place" },
};

const VALUES = [
  { icon: Layers, title: "Works for any business", text: "Pharmacy, hardware, bookstore or distributor: define your own categories, fields and units." },
  { icon: Boxes, title: "Stock you can trust", text: "Every change is recorded permanently, and concurrent sales can't oversell." },
  { icon: ShieldCheck, title: "Your data stays yours", text: "Each business is fully isolated; one business can never see another's records." },
  { icon: Smartphone, title: "Web and mobile", text: "An Android app for the shop floor and this web dashboard for the office." },
];

const STEPS = [
  { title: "Create your workspace", text: "Register your business. You get default units and a main warehouse straight away." },
  { title: "Add products and opening stock", text: "Set up categories, custom fields and products, with purchase and selling prices." },
  { title: "Record sales and purchases", text: "Sales deduct stock and purchases add it, with payments, returns and cancellations." },
  { title: "Invite your team and trade", text: "Give employees the right role, then connect with businesses you buy from or sell to." },
];

const FAQ = [
  { q: "What kinds of businesses can use it?", a: "Any business that keeps stock. Each business defines its own categories, custom product fields (text, number, date, yes/no, dropdown, multi-select), units and warehouses. The business type is a description only and never limits features." },
  { q: "How do B2B invoices work?", a: "You connect with another business on the platform, then send it an invoice. Sending reserves your stock. When the buyer approves, a sale is recorded for you and a purchase for them in one transaction, so the buyer never re-types anything. Either side can stop a pending invoice, which releases the reservation." },
  { q: "Is purchase price kept separate from selling price?", a: "Yes. Each product has a purchase price (what you pay your supplier or dealer) and a selling price. Purchases update a weighted-average cost, and each sale saves the price and cost of that day, so later price changes never rewrite history." },
  { q: "Can I control what employees see and do?", a: "Yes. There are three roles — Admin, Inventory Manager and Staff — with default permissions, and admins can override individual permissions per person. Staff without analytics access don't see costs or profit." },
  { q: "Does it calculate profit?", a: "It reports net sales and gross profit using the cost saved on each sale line. If any item in a period has no cost recorded, profit is shown as unavailable rather than guessed." },
  { q: "Is it a full accounting system?", a: "No. It records inventory, sales, purchases, payments and profit, and exports reports to CSV or Excel, but it does not replace your accounting software." },
  { q: "Can I try it before signing up?", a: "Yes. The demo dashboard runs entirely on sample data in your browser. Nothing you do there is saved." },
];

export default function HomePage() {
  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden border-b border-line bg-gradient-to-b from-brand-50/70 to-white">
        <Container className="grid items-center gap-12 py-16 sm:py-20 lg:grid-cols-[1.05fr_1fr]">
          <div>
            <p className="inline-flex items-center gap-2 rounded-full border border-brand-200 bg-white px-3 py-1 text-xs font-medium text-brand-700">
              Inventory · Sales · Purchases · B2B invoicing
            </p>
            <h1 className="mt-5 text-4xl font-semibold leading-[1.1] tracking-tight text-navy-950 sm:text-5xl">
              Manage inventory and B2B operations in one place
            </h1>
            <p className="mt-5 max-w-xl text-lg leading-relaxed text-muted">
              Track products and stock across warehouses, record sales and purchases, and invoice the businesses you trade with — with roles and permissions for your whole team.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <ButtonLink href="/signup" size="lg">Create your workspace <ArrowRight className="size-4" aria-hidden /></ButtonLink>
              <ButtonLink href="/dashboard/demo" size="lg" variant="secondary">Explore the demo</ButtonLink>
            </div>
            <ul className="mt-8 grid gap-2 text-sm text-ink-2 sm:grid-cols-2">
              {["Multi-business workspaces", "Low-stock alerts", "Purchase vs selling price", "CSV and Excel exports"].map((t) => (
                <li key={t} className="flex items-center gap-2"><CheckCircle2 className="size-4 text-accent-700" aria-hidden />{t}</li>
              ))}
            </ul>
          </div>
          <DashboardPreview />
        </Container>
      </section>

      {/* Value strip */}
      <section aria-label="Why teams use it" className="border-b border-line">
        <Container className="grid gap-6 py-10 sm:grid-cols-2 lg:grid-cols-4">
          {VALUES.map((v) => (
            <div key={v.title} className="flex gap-3">
              <v.icon className="mt-0.5 size-5 shrink-0 text-brand-600" aria-hidden />
              <div>
                <p className="font-semibold text-navy-950">{v.title}</p>
                <p className="mt-1 text-sm text-muted">{v.text}</p>
              </div>
            </div>
          ))}
        </Container>
      </section>

      {/* Features */}
      <section className="bg-canvas py-20">
        <Container>
          <SectionHeading eyebrow="Features" title="Everything between the stockroom and the invoice" description="Built around the way stock actually moves: every sale, purchase, transfer and correction is recorded, and nothing is overwritten." />
          <div className="mt-10"><FeatureGrid /></div>
          <div className="mt-8"><ButtonLink href="/features" variant="secondary">See all features <ArrowRight className="size-4" aria-hidden /></ButtonLink></div>
        </Container>
      </section>

      {/* How it works */}
      <section className="py-20">
        <Container>
          <SectionHeading eyebrow="How it works" title="Up and running in four steps" center />
          <ol className="mt-12 grid gap-6 md:grid-cols-4">
            {STEPS.map((s, i) => (
              <li key={s.title} className="relative rounded-xl border border-line bg-white p-5">
                <span className="grid size-8 place-items-center rounded-full bg-navy-950 text-sm font-semibold text-white" aria-hidden>{i + 1}</span>
                <h3 className="mt-4 font-semibold text-navy-950">{s.title}</h3>
                <p className="mt-1.5 text-sm text-muted">{s.text}</p>
              </li>
            ))}
          </ol>
        </Container>
      </section>

      {/* Use cases */}
      <section className="bg-canvas py-20">
        <Container>
          <SectionHeading eyebrow="Solutions" title="Fits the way your business trades" description="The same platform adapts to how you buy, stock and sell." />
          <div className="mt-10"><UseCaseGrid /></div>
        </Container>
      </section>

      {/* Dashboard preview */}
      <section className="py-20">
        <Container className="grid items-center gap-10 lg:grid-cols-2">
          <div>
            <SectionHeading eyebrow="Dashboard" title="See what needs attention today" description="Stock levels, low-stock alerts, sales and profit trends, recent activity and invoices waiting for approval — on one screen, shown according to each person's permissions." />
            <div className="mt-6 flex flex-wrap gap-3">
              <ButtonLink href="/dashboard/demo">Open the demo dashboard</ButtonLink>
            </div>
            <p className="mt-3 text-xs text-muted">The demo uses sample data and runs in your browser. Nothing is saved.</p>
          </div>
          <DashboardPreview />
        </Container>
      </section>

      {/* FAQ */}
      <section className="bg-canvas py-20">
        <Container className="grid gap-10 lg:grid-cols-[1fr_1.6fr]">
          <SectionHeading eyebrow="FAQ" title="Common questions" description="Have a question that isn't answered here? Get in touch." />
          <div className="divide-y divide-line rounded-xl border border-line bg-white">
            {FAQ.map((f) => (
              <details key={f.q} className="group px-5 py-4">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-medium text-navy-950">
                  {f.q}
                  <span className="text-xl leading-none text-muted transition-transform group-open:rotate-45" aria-hidden>+</span>
                </summary>
                <p className="mt-3 text-sm leading-relaxed text-muted">{f.a}</p>
              </details>
            ))}
          </div>
        </Container>
      </section>

      <ClosingCta />
    </>
  );
}
