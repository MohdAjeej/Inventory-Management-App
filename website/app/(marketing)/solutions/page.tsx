import type { Metadata } from "next";
import { ClosingCta, Container, PageIntro, SectionHeading, UseCaseGrid } from "@/components/marketing/sections";

export const metadata: Metadata = {
  title: "Solutions",
  description: "How wholesalers, distributors, retailers and multi-business operators use the Inventory Platform.",
};

const FLOW = [
  { who: "Wholesaler", step: "Creates an invoice for a connected retailer and sends it. The stock is reserved." },
  { who: "Retailer", step: "Reviews where each line will go in their own stock: a linked product, a match by barcode or name, or a new product." },
  { who: "Both", step: "On approval, a sale is recorded for the wholesaler and a purchase for the retailer, in one transaction." },
  { who: "Retailer", step: "If goods arrive damaged, requests a return. When the wholesaler accepts, stock moves back." },
];

export default function SolutionsPage() {
  return (
    <>
      <PageIntro eyebrow="Solutions" title="Built for businesses that buy, stock and sell" description="Whether you sell in bulk, run several warehouses or serve customers at the counter, the platform adapts through categories, custom fields, units and roles — not separate editions." />
      <section className="py-16">
        <Container><UseCaseGrid /></Container>
      </section>
      <section className="border-t border-line bg-canvas py-16">
        <Container>
          <SectionHeading eyebrow="Wholesaler to retailer" title="B2B invoicing without re-typing" description="Connected businesses exchange invoices directly. Here is what happens at each step." />
          <ol className="mt-10 grid gap-4 md:grid-cols-4">
            {FLOW.map((f, i) => (
              <li key={i} className="rounded-xl border border-line bg-white p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-accent-700">Step {i + 1} · {f.who}</p>
                <p className="mt-2 text-sm text-ink-2">{f.step}</p>
              </li>
            ))}
          </ol>
        </Container>
      </section>
      <ClosingCta />
    </>
  );
}
