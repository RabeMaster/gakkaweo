import { useCallback, useState } from "react";

const STORAGE_PREFIX = "gakkaweo-guesses-";

export interface AnonymousGuess {
  guessText: string;
  similarity: number;
  attemptNumber: number;
  timestamp: string;
}

interface AnonymousGameState {
  guesses: AnonymousGuess[];
  attemptCount: number;
  bestSimilarity: number;
  isCleared: boolean;
}

const EMPTY_STATE: AnonymousGameState = { guesses: [], attemptCount: 0, bestSimilarity: 0, isCleared: false };

function loadState(sentenceId: string): AnonymousGameState {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + sentenceId);
    if (raw) {
      return JSON.parse(raw);
    }
  } catch {
    // ignore parse errors
  }
  return EMPTY_STATE;
}

function saveState(sentenceId: string, state: AnonymousGameState) {
  localStorage.setItem(STORAGE_PREFIX + sentenceId, JSON.stringify(state));
}

export function useAnonymousGame(sentenceId: string | undefined) {
  const [trackedId, setTrackedId] = useState(sentenceId);
  const [state, setState] = useState<AnonymousGameState>(() => (sentenceId ? loadState(sentenceId) : EMPTY_STATE));

  if (sentenceId !== trackedId) {
    setTrackedId(sentenceId);
    setState(sentenceId ? loadState(sentenceId) : EMPTY_STATE);
  }

  const addGuess = useCallback(
    (guessText: string, similarity: number, isCorrect: boolean) => {
      if (!sentenceId) {
        return;
      }

      setState((prev) => {
        const attemptCount = prev.isCleared ? prev.attemptCount : prev.attemptCount + 1;
        const bestSimilarity = Math.max(prev.bestSimilarity, similarity);
        const isCleared = prev.isCleared || isCorrect;

        const newGuess: AnonymousGuess = {
          guessText,
          similarity,
          attemptNumber: prev.guesses.length + 1,
          timestamp: new Date().toISOString(),
        };

        const next: AnonymousGameState = {
          guesses: [...prev.guesses, newGuess],
          attemptCount,
          bestSimilarity,
          isCleared,
        };

        saveState(sentenceId, next);
        return next;
      });
    },
    [sentenceId],
  );

  return { state, addGuess };
}
