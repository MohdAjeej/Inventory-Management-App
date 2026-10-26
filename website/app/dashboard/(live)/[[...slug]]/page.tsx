import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DashboardScreen } from "@/components/dashboard/app";
import { matchScreen } from "@/lib/routes";

type Props = { params: Promise<{ slug?: string[] }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  return { title: matchScreen(slug)?.title ?? "Dashboard", robots: { index: false } };
}

/** Live dashboard: data comes from the Spring backend through /api/backend. */
export default async function LiveDashboardPage({ params }: Props) {
  const { slug = [] } = await params;
  if (!matchScreen(slug)) notFound();
  return <DashboardScreen slug={slug} />;
}
