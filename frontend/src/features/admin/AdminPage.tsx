import { Outlet } from "react-router-dom";
import { AdminSidebar } from "@/features/admin/components/AdminSidebar";

export function AdminPage() {
  return (
    <div className="flex gap-6 items-start min-h-[calc(100vh-120px)]">
      <AdminSidebar />
      <div className="flex-1 min-w-0">
        <Outlet />
      </div>
    </div>
  );
}
