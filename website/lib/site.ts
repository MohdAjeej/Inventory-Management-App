/** Public site configuration. Contact details are placeholders until the business provides real ones. */
export const siteConfig = {
  name: "Inventory Platform",
  description:
    "Manage products, stock, sales, purchases, customers, suppliers and business-to-business invoices for any kind of business — from one workspace.",
  url: process.env.NEXT_PUBLIC_SITE_URL ?? "http://147.93.108.99:8082/",
  contact: {
    email: "hello@example.com",
    phone: "+00 0000 000000",
    note: "Placeholder contact details — replace before launch.",
  },
} as const;

export const marketingNav = [
  { href: "/features", label: "Features" },
  { href: "/solutions", label: "Solutions" },
  { href: "/about", label: "About" },
  { href: "/contact", label: "Contact" },
] as const;
