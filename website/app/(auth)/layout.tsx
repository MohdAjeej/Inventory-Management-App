import Link from "next/link";
import { Logo } from "@/components/logo";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <header className="px-4 py-5 sm:px-6">
        <Link href="/" className="inline-block rounded" aria-label="Inventory Platform home">
          <Logo />
        </Link>
      </header>
      <main id="main" className="flex flex-1 items-start justify-center px-4 pb-16 pt-4 sm:pt-10">
        {children}
      </main>
    </div>
  );
}
