import type { Metadata } from "next";
import Link from "next/link";
import { JoinForm } from "@/components/auth/auth-forms";
import { AuthCard } from "../auth-card";

export const metadata: Metadata = { title: "Join a business", description: "Accept an invitation to join a business workspace." };

export default function JoinPage() {
  return (
    <AuthCard title="Join a business" description="Enter the invitation code your business admin shared with you." footer={<Link href="/login" className="font-medium text-brand-700 hover:underline">Back to sign in</Link>}>
      <JoinForm />
    </AuthCard>
  );
}
