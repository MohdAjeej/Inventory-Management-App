import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DashboardScreen } from "@/components/dashboard/app";
import { matchScreen } from "@/lib/routes";

type Props = { params: Promise<{ slug?: string[] }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  return { title: `${matchScreen(slug)?.title ?? "Dashboard"} (demo)`, robots: { index: false } };
}

/** Demo dashboard: same screens, in-memory sample data (lib/demo). Clearly labelled in the UI. */
export default async function DemoDashboardPage({ params }: Props) {
  const { slug = [] } = await params;
  if (!matchScreen(slug)) notFound();
  return <DashboardScreen slug={slug} />;
}
