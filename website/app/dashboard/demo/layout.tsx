import { DashboardLayout } from "@/components/dashboard/app";

export default function Layout({ children }: { children: React.ReactNode }) {
  return <DashboardLayout mode="demo">{children}</DashboardLayout>;
}
