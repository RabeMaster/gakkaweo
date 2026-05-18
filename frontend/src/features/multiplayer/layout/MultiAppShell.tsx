import { Outlet } from "react-router-dom";
import { DesktopOnlyGate } from "@/shared/ui/DesktopOnlyGate";
import { useStompClient } from "@/shared/hooks/useStompClient";

function MultiContent() {
  useStompClient();
  return <Outlet />;
}

export function MultiAppShell() {
  return (
    <DesktopOnlyGate>
      <MultiContent />
    </DesktopOnlyGate>
  );
}
