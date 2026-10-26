import type { Metadata } from "next";
import { ClosingCta, Container, PageIntro } from "@/components/marketing/sections";

export const metadata: Metadata = {
  title: "About",
  description: "What the Inventory Platform is, how it is built, and the principles behind it.",
};

const PRINCIPLES = [
  { title: "History is never rewritten", text: "Stock changes are permanent movement records. Sales keep the price and cost of the day. Cancelling a sale reverses it with new records instead of deleting it." },
  { title: "Totals come from the server", text: "Line amounts, tax, stock and profit are calculated by the backend in one transaction, so every screen and device shows the same numbers." },
  { title: "Businesses are isolated", text: "The business is always taken from your signed-in membership, never from a request. Another business's records simply don't exist for you." },
  { title: "Unknown means unknown", text: "If a cost is missing, profit is shown as unavailable rather than estimated as zero." },
];

export default function AboutPage() {
  return (
    <>
      <PageIntro eyebrow="About" title="A multi-business inventory platform" description="The Inventory Platform lets any kind of business configure its own catalogue and track stock, sales, purchases, payments and profit — and lets connected businesses invoice each other." />
      <section className="py-16">
        <Container className="grid gap-12 lg:grid-cols-2">
          <div>
            <h2 className="text-2xl font-semibold text-navy-950">How it&apos;s built</h2>
            <div className="mt-4 space-y-4 text-sm leading-relaxed text-ink-2">
              <p>A Java and Spring Boot backend with a PostgreSQL database holds the business rules: stock movements, sales and purchase transactions, permissions and business-to-business invoicing.</p>
              <p>Two clients use the same API: an Android app built for the shop floor, and this web dashboard for owners, managers and office staff.</p>
              <p>Every request is authorised on the server against the member&apos;s role and permissions, so hiding a button is never the only protection.</p>
            </div>
          </div>
          <div>
            <h2 className="text-2xl font-semibold text-navy-950">Principles</h2>
            <ul className="mt-4 space-y-4">
              {PRINCIPLES.map((p) => (
                <li key={p.title} className="rounded-lg border border-line p-4">
                  <p className="font-semibold text-navy-950">{p.title}</p>
                  <p className="mt-1 text-sm text-muted">{p.text}</p>
                </li>
              ))}
            </ul>
          </div>
        </Container>
      </section>
      <ClosingCta />
    </>
  );
}
