import { Check } from "lucide-react";
import type { Metadata } from "next";
import { ClosingCta, Container, FEATURES, PageIntro } from "@/components/marketing/sections";

export const metadata: Metadata = {
  title: "Features",
  description: "Product catalogue, stock tracking, sales and purchases, customers and suppliers, B2B invoices, employee permissions, analytics and warehouses.",
};

export default function FeaturesPage() {
  return (
    <>
      <PageIntro eyebrow="Features" title="One platform for stock, trade and your team" description="Everything listed here is available today in the platform's mobile app and backend. The web dashboard covers the day-to-day screens; a few setup tasks are still done in the mobile app." />
      <section className="py-16">
        <Container className="grid gap-6 md:grid-cols-2">
          {FEATURES.map((f) => (
            <article key={f.title} className="rounded-xl border border-line bg-white p-6 shadow-[var(--shadow-card)]">
              <div className="flex items-center gap-3">
                <span className="grid size-10 place-items-center rounded-lg bg-brand-50 text-brand-600"><f.icon className="size-5" aria-hidden /></span>
                <h2 className="text-lg font-semibold text-navy-950">{f.title}</h2>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-muted">{f.text}</p>
              <ul className="mt-4 space-y-2 text-sm text-ink-2">
                {f.points.map((p) => <li key={p} className="flex gap-2"><Check className="mt-0.5 size-4 shrink-0 text-accent-700" aria-hidden />{p}</li>)}
              </ul>
            </article>
          ))}
        </Container>
      </section>
      <section className="border-t border-line bg-canvas py-16">
        <Container>
          <h2 className="text-2xl font-semibold text-navy-950">Good to know</h2>
          <ul className="mt-6 grid gap-4 text-sm text-ink-2 md:grid-cols-2">
            <li className="rounded-lg border border-line bg-white p-4">It is not a full accounting system. It records inventory, sales, purchases, payments and profit.</li>
            <li className="rounded-lg border border-line bg-white p-4">Notifications are in-app; push delivery and email invitations are not configured yet. Invitations are shared as codes.</li>
            <li className="rounded-lg border border-line bg-white p-4">Products tracked by batch or serial number can&apos;t be put on business invoices yet.</li>
            <li className="rounded-lg border border-line bg-white p-4">Units are never converted automatically between businesses on an invoice.</li>
          </ul>
        </Container>
      </section>
      <ClosingCta />
    </>
  );
}
