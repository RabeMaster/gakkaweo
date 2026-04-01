import { getSimilarityColor } from "@/shared/utils/similarity";

interface GuessInfo {
  guessText: string;
  similarity: number;
}

interface GuessFeedbackProps {
  error: string | null;
  lastGuess: GuessInfo | null;
  bestGuess: GuessInfo | null;
}

function FeedbackRow({ label, guess }: { label: string; guess: GuessInfo | null }) {
  if (!guess) {
    return (
      <div className="h-6 flex items-center">
        <span className="text-sm font-bold text-gray-400 dark:text-gray-500">{label}: —</span>
      </div>
    );
  }

  return (
    <div className="h-6 flex items-center justify-between gap-2">
      <p className="text-sm font-bold text-gray-700 dark:text-gray-300 truncate min-w-0">
        <span className="text-gray-500 dark:text-gray-400">{label}:</span> {guess.guessText}
      </p>
      <span
        className="text-sm font-black tabular-nums shrink-0"
        style={{ color: getSimilarityColor(guess.similarity).bg }}
      >
        {guess.similarity.toFixed(1)}%
      </span>
    </div>
  );
}

export function GuessFeedback({ error, lastGuess, bestGuess }: GuessFeedbackProps) {
  return (
    <div className="space-y-1">
      <FeedbackRow label="최고 유사도" guess={bestGuess} />
      {error ? (
        <div className="h-6 flex items-center">
          <p className="text-sm font-bold text-red-500 truncate">{error}</p>
        </div>
      ) : (
        <FeedbackRow label="마지막 추측" guess={lastGuess} />
      )}
    </div>
  );
}
