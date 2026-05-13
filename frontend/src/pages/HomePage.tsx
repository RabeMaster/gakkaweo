import { GamePage } from "@/features/game/GamePage";
import { HintPanel } from "@/features/game/components/HintPanel";
import { useToday, useGameStatus, useHints } from "@/features/game/hooks/useGameQueries";
import { RankingPanel } from "@/features/ranking/components/RankingPanel";
import { useRankings } from "@/features/ranking/hooks/useRankingQueries";
import { useAuthStore } from "@/shared/stores/useAuthStore";

export function HomePage() {
  const { data: ranking, isLoading: rankingLoading } = useRankings();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const { data: today } = useToday();
  const sentenceId = today?.sentenceId;
  const { data: status } = useGameStatus(sentenceId);
  const bestSimilarity = status?.bestSimilarity ?? 0;

  const { data: hints, isLoading: hintsLoading } = useHints(sentenceId, bestSimilarity);

  return (
    <div className="flex flex-col md:flex-row gap-6 md:items-start">
      <div className="order-2 md:order-none w-full md:w-72 md:shrink-0 space-y-6">
        <RankingPanel ranking={ranking} isLoading={rankingLoading} />
        <HintPanel
          hints={hints?.hints ?? []}
          isLoading={hintsLoading}
          bestSimilarity={bestSimilarity}
          isAuthenticated={isAuthenticated}
        />
      </div>
      <GamePage />
    </div>
  );
}
