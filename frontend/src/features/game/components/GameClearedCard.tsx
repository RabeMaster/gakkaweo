import { Card } from "@/shared/ui/Card";
import { Button } from "@/shared/ui/Button";
import { useToastStore } from "@/shared/stores/useToastStore";

interface GameClearedCardProps {
  attemptCount: number;
  bestSimilarity: number;
  similarities: number[];
}

export function GameClearedCard({ attemptCount, bestSimilarity, similarities }: GameClearedCardProps) {
  const addToast = useToastStore((s) => s.addToast);

  async function handleCopy() {
    const path = similarities.map((s) => s.toFixed(1)).join(" → ");
    const text = `가까워 🎉 ${attemptCount}회 만에 성공!\n최고 유사도: ${path}`;

    try {
      await navigator.clipboard.writeText(text);
      addToast("결과가 복사되었습니다!", "success");
    } catch {
      addToast("복사에 실패했습니다.", "error");
    }
  }

  return (
    <Card className="bg-green-50 dark:bg-green-950">
      <div className="space-y-4 text-center">
        <p className="text-3xl font-black">🎉 정답!</p>
        <p className="text-lg font-bold">{attemptCount}회 만에 맞추셨습니다!</p>
        <p className="text-base font-medium text-gray-600 dark:text-gray-400">
          최고 유사도: {bestSimilarity.toFixed(1)}%
        </p>
        <Button onClick={handleCopy} variant="secondary" size="sm">
          결과 복사
        </Button>
      </div>
    </Card>
  );
}
