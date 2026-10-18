import { useState, useEffect, useRef } from "react";
import { shareGameResult } from "@/features/game/shareResult";
import { Card } from "@/shared/ui/Card";
import { Button } from "@/shared/ui/Button";
import { useToastStore } from "@/shared/stores/useToastStore";

interface GameClearedCardProps {
  attemptCount: number;
  bestSimilarity: number;
}

export function GameClearedCard({ attemptCount, bestSimilarity }: GameClearedCardProps) {
  const [sharing, setSharing] = useState(false);
  const addToast = useToastStore((state) => state.addToast);
  const mounted = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  async function shareResult() {
    if (sharing) {
      return;
    }
    setSharing(true);
    const text = `가까워 오늘의 문장을 ${attemptCount}회 만에 해결했습니다! 최고 유사도 ${bestSimilarity.toFixed(1)}%`;
    const url = `${location.origin}/`;
    try {
      const result = await shareGameResult(text, url);
      if (!mounted.current) {
        return;
      }
      if (result === "copied") {
        addToast("게임 결과와 링크를 복사했습니다.", "success");
      } else if (result === "unsupported") {
        addToast("이 브라우저에서는 공유를 지원하지 않습니다.", "info");
      }
    } catch {
      if (mounted.current) {
        addToast("공유할 수 없습니다. 브라우저 권한을 확인해주세요.", "error");
      }
    } finally {
      if (mounted.current) {
        setSharing(false);
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
        <Button size="sm" variant="secondary" onClick={shareResult} isLoading={sharing}>
          결과 공유
        </Button>
      </div>
    </Card>
  );
}
