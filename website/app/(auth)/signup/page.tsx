import type { Metadata } from "next";
import Link from "next/link";
import { SignupForm } from "@/components/auth/auth-forms";
import { AuthCard } from "../auth-card";

export const metadata: Metadata = { title: "Create workspace", description: "Register your business on the Inventory Platform." };

export default function SignupPage() {
  return (
    <AuthCard
      title="Create your workspace"
      description="Register your business and its first administrator."
      footer={<>Already have an account? <Link href="/login" className="font-medium text-brand-700 hover:underline">Sign in</Link> · Joining a team? <Link href="/join" className="font-medium text-brand-700 hover:underline">Use an invitation code</Link></>}
    >
      <SignupForm />
    </AuthCard>
  );
}
