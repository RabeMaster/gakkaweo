import { useState } from "react";
import type { ReactNode } from "react";
import { AdminSidebar } from "@/features/admin/components/AdminSidebar";
import { DashboardTab } from "@/features/admin/components/DashboardTab";
import { SentenceTab } from "@/features/admin/components/SentenceTab";
import { UserTab } from "@/features/admin/components/UserTab";
import { SystemTab } from "@/features/admin/components/SystemTab";

type Tab = "dashboard" | "sentences" | "users" | "system";

const TAB_COMPONENTS: Record<Tab, () => ReactNode> = {
  dashboard: DashboardTab,
  sentences: SentenceTab,
  users: UserTab,
  system: SystemTab,
};

export function AdminPage() {
  const [activeTab, setActiveTab] = useState<Tab>("dashboard");
  const ActiveComponent = TAB_COMPONENTS[activeTab];

  return (
    <div className="flex gap-6 items-start min-h-[calc(100vh-120px)]">
      <AdminSidebar activeTab={activeTab} onTabChange={setActiveTab} />
      <div className="flex-1 min-w-0">
        <ActiveComponent />
      </div>
    </div>
  );
}
