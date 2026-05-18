import type { ReactNode } from "react";
import { useMediaQuery } from "@/shared/hooks/useMediaQuery";
import { useNoIndex } from "@/shared/hooks/useNoIndex";
import { Card } from "@/shared/ui/Card";

export function DesktopOnlyGate({ children }: { children: ReactNode }) {
  const isDesktop = useMediaQuery("(min-width: 768px)");
  useNoIndex();

  if (!isDesktop) {
    return (
      <div className="flex flex-1 items-center justify-center p-8">
        <Card className="!bg-yellow-100 text-center dark:!bg-gray-900">
          <p className="text-xl font-extrabold">데스크톱에서 이용해주세요</p>
          <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
            이 기능은 768px 이상의 화면에서만 이용할 수 있습니다.
          </p>
        </Card>
      </div>
    );
  }

  return <>{children}</>;
}
