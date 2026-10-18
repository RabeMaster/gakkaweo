import { useState, useEffect, useRef } from "react";
import { copyGameResult, shareGameResult } from "@/features/game/shareResult";
import { Card } from "@/shared/ui/Card";
import { Button } from "@/shared/ui/Button";
import { useToastStore } from "@/shared/stores/useToastStore";

interface GameClearedCardProps {
  attemptCount: number;
  bestSimilarity: number;
}

export function GameClearedCard({ attemptCount, bestSimilarity }: GameClearedCardProps) {
  const [pendingAction, setPendingAction] = useState<"share" | "copy" | null>(null);
  const addToast = useToastStore((state) => state.addToast);
  const mounted = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  async function handleResultAction(action: "share" | "copy") {
    if (pendingAction) {
      return;
    }

    setPendingAction(action);
    const text = `🎯 가까워 | 오늘의 문장\n\n✨ ${attemptCount}번의 도전 끝에 정답!\n📈 최고 유사도 ${bestSimilarity.toFixed(1)}%\n\n오늘의 문장에 도전해 보세요!`;

    try {
      const result = await (action === "copy" ? copyGameResult(text) : shareGameResult(text));
      if (!mounted.current) {
        return;
      }
      if (result === "copied") {
        addToast("게임 결과와 링크를 복사했습니다.", "success");
      } else if (result === "unsupported") {
        addToast(
          action === "copy"
            ? "이 브라우저에서는 복사를 지원하지 않습니다."
            : "이 브라우저에서는 공유를 지원하지 않습니다.",
          "info",
        );
      }
    } catch {
      if (mounted.current) {
        addToast(
          action === "copy"
            ? "복사할 수 없습니다. 브라우저 권한을 확인해주세요."
            : "공유할 수 없습니다. 브라우저 권한을 확인해주세요.",
          "error",
        );
      }
    } finally {
      if (mounted.current) {
        setPendingAction(null);
      }
    }
  }

  return (
    <Card className="!bg-green-50 dark:!bg-green-950">
      <div className="space-y-2 text-center">
        <p className="text-2xl md:text-3xl font-black">🎉 정답!</p>
        <p className="text-lg font-bold">{attemptCount}회 만에 맞추셨습니다!</p>
        <p className="text-base font-medium text-gray-600 dark:text-gray-400">
          최고 유사도: {bestSimilarity.toFixed(1)}%
        </p>
        <div className="flex justify-center gap-3">
          <Button
            size="sm"
            variant="secondary"
            onClick={() => handleResultAction("copy")}
            isLoading={pendingAction === "copy"}
            disabled={pendingAction !== null}
          >
            결과 복사
          </Button>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => handleResultAction("share")}
            isLoading={pendingAction === "share"}
            disabled={pendingAction !== null}
          >
            결과 공유
          </Button>
        </div>
      </div>
    </Card>
  );
}
