import { GamePage } from "@/features/game/GamePage";
import { HintPanel } from "@/features/game/components/HintPanel";
import { useToday, useGameStatus, useHints } from "@/features/game/hooks/useGameQueries";
import { RankingPanel } from "@/features/ranking/components/RankingPanel";
import { useRankings } from "@/features/ranking/hooks/useRankingQueries";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { MobileSideSheet } from "@/shared/ui/MobileSideSheet";

export function HomePage() {
  const { data: ranking, isLoading: rankingLoading } = useRankings();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const { data: today } = useToday();
  const sentenceId = today?.sentenceId;
  const { data: status } = useGameStatus(sentenceId);
  const bestSimilarity = status?.bestSimilarity ?? 0;

  const { data: hints, isLoading: hintsLoading } = useHints(sentenceId, bestSimilarity);

  const rankingPanel = <RankingPanel ranking={ranking} isLoading={rankingLoading} />;
  const hintPanel = (
    <HintPanel
      hints={hints?.hints ?? []}
      isLoading={hintsLoading}
      bestSimilarity={bestSimilarity}
      isAuthenticated={isAuthenticated}
    />
  );

  return (
    <>
      <div className="flex flex-col md:flex-row gap-6 md:items-start">
        <div className="hidden md:block w-72 shrink-0 space-y-6">
          {rankingPanel}
          {hintPanel}
        </div>
        <GamePage />
      </div>

      <MobileSideSheet
        tabs={[
          { key: "ranking", label: "랭킹", content: rankingPanel },
          { key: "hint", label: "힌트", content: hintPanel },
        ]}
      />
    </>
  );
}
