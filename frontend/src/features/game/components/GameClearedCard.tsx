import { Card } from "@/shared/ui/Card";

interface GameClearedCardProps {
  attemptCount: number;
  bestSimilarity: number;
}

export function GameClearedCard({ attemptCount, bestSimilarity }: GameClearedCardProps) {
  return (
    <Card className="!bg-green-50 dark:!bg-green-950">
      <div className="space-y-2 text-center">
        <p className="text-3xl font-black">🎉 정답!</p>
        <p className="text-lg font-bold">{attemptCount}회 만에 맞추셨습니다!</p>
        <p className="text-base font-medium text-gray-600 dark:text-gray-400">
          최고 유사도: {bestSimilarity.toFixed(1)}%
        </p>
      </div>
    </Card>
  );
}
