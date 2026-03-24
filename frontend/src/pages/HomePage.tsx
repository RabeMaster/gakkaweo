import { GamePage } from "@/features/game/GamePage";
import { RankingPanel } from "@/features/ranking/components/RankingPanel";
import { useRankings } from "@/features/ranking/hooks/useRankingQueries";
import { useRankingSSE } from "@/features/ranking/hooks/useRankingSSE";

export function HomePage() {
  const { data: ranking, isLoading } = useRankings();
  useRankingSSE();

  return (
    <div className="flex gap-6 items-start">
      <RankingPanel ranking={ranking} isLoading={isLoading} />
      <GamePage />
    </div>
  );
}
