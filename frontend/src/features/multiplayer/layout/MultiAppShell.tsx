import { Outlet } from "react-router-dom";
import { DesktopOnlyGate } from "@/shared/ui/DesktopOnlyGate";
import { useStompClient } from "@/shared/hooks/useStompClient";

export function MultiAppShell() {
  useStompClient();

  return (
    <DesktopOnlyGate>
      <Outlet />
    </DesktopOnlyGate>
  );
}
