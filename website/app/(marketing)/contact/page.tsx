import { Mail, Phone } from "lucide-react";
import type { Metadata } from "next";
import { ContactForm } from "@/components/marketing/contact-form";
import { Container, PageIntro } from "@/components/marketing/sections";
import { siteConfig } from "@/lib/site";

export const metadata: Metadata = {
  title: "Contact",
  description: "Questions about the Inventory Platform? Send us a message.",
};

export default function ContactPage() {
  return (
    <>
      <PageIntro eyebrow="Contact" title="Talk to us" description="Questions about features, setting up your workspace, or B2B invoicing with your trading partners? Send a message." />
      <section className="py-16">
        <Container className="grid gap-10 lg:grid-cols-[1.6fr_1fr]">
          <ContactForm />
          <aside className="space-y-4">
            <div className="rounded-xl border border-line p-5">
              <h2 className="font-semibold text-navy-950">Other ways to reach us</h2>
              <ul className="mt-3 space-y-3 text-sm text-ink-2">
                <li className="flex items-center gap-2"><Mail className="size-4 text-brand-600" aria-hidden />{siteConfig.contact.email}</li>
                <li className="flex items-center gap-2"><Phone className="size-4 text-brand-600" aria-hidden />{siteConfig.contact.phone}</li>
              </ul>
              <p className="mt-3 text-xs text-muted">{siteConfig.contact.note}</p>
            </div>
            <div className="rounded-xl border border-line bg-canvas p-5 text-sm text-ink-2">
              <p className="font-semibold text-navy-950">Already using the platform?</p>
              <p className="mt-1">Sign in to your workspace, or ask your business admin for an invitation code.</p>
            </div>
          </aside>
        </Container>
      </section>
    </>
  );
}
