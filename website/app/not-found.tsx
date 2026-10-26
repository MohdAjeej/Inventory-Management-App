import Link from "next/link";
import { Logo } from "@/components/logo";
import { ButtonLink } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="grid min-h-dvh place-items-center bg-canvas px-4">
      <div className="text-center">
        <Link href="/" className="inline-block"><Logo /></Link>
        <p className="mt-8 text-sm font-semibold text-brand-600">404</p>
        <h1 className="mt-1 text-2xl font-semibold text-navy-950">Page not found</h1>
        <p className="mt-2 text-sm text-muted">The page you&apos;re looking for doesn&apos;t exist or has moved.</p>
        <div className="mt-6 flex justify-center gap-2">
          <ButtonLink href="/">Home</ButtonLink>
          <ButtonLink href="/dashboard" variant="secondary">Dashboard</ButtonLink>
        </div>
      </div>
    </main>
  );
}
