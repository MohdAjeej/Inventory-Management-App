import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/auth-forms";
import { AuthCard } from "../auth-card";

export const metadata: Metadata = { title: "Sign in", description: "Sign in to your Inventory Platform workspace." };

export default function LoginPage() {
  return (
    <AuthCard
      title="Sign in"
      description="Welcome back. Sign in to your business workspace."
      footer={<>New here? <Link href="/signup" className="font-medium text-brand-700 hover:underline">Create a workspace</Link> · <Link href="/dashboard/demo" className="font-medium text-brand-700 hover:underline">View the demo</Link></>}
    >
      <Suspense>
        <LoginForm />
      </Suspense>
    </AuthCard>
  );
}
