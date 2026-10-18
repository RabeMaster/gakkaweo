import { useCallback, useState } from "react";
import {
  EMPTY_ANONYMOUS_STATE,
  parseAnonymousState,
  type AnonymousGuess,
  type AnonymousGameState,
} from "@/features/game/anonymousState";

const STORAGE_PREFIX = "gakkaweo-guesses-";

const EMPTY_STATE = EMPTY_ANONYMOUS_STATE;

function loadState(sentenceId: string): AnonymousGameState {
  try {
    return parseAnonymousState(localStorage.getItem(STORAGE_PREFIX + sentenceId));
  } catch {
    // 파싱 오류는 무시합니다.
  }
  return EMPTY_STATE;
}

function saveState(sentenceId: string, state: AnonymousGameState) {
  try {
    localStorage.setItem(STORAGE_PREFIX + sentenceId, JSON.stringify(state));
  } catch {
    // 저장소가 차단되거나 가득 차도 메모리에서 게임을 계속합니다.
  }
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
