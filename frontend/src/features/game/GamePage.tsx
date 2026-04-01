import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import confetti from "canvas-confetti";
import { ApiError } from "@/shared/api/client";
import type { GuessResponse, HistoryResponse } from "@/shared/api/types";
import { Card } from "@/shared/ui/Card";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { useToastStore } from "@/shared/stores/useToastStore";
import { useToday, useGameStatus, useGameHistory, useGuess } from "@/features/game/hooks/useGameQueries";
import { useCountdown } from "@/features/game/hooks/useCountdown";
import { useAnonymousGame } from "@/features/game/hooks/useAnonymousGame";
import { HintMask } from "@/features/game/components/HintMask";
import { GuessInput } from "@/features/game/components/GuessInput";
import { GuessHistory } from "@/features/game/components/GuessHistory";
import { GameClearedCard } from "@/features/game/components/GameClearedCard";
import { YesterdayAnswer } from "@/features/game/components/YesterdayAnswer";
import { HelpModal, HELP_SHOWN_KEY } from "@/features/game/components/HelpModal";
import { getSoundVolume } from "@/shared/config/sound";

export function GamePage() {
  const queryClient = useQueryClient();
  const addToast = useToastStore((s) => s.addToast);
  const { isAuthenticated, isLoading: authLoading } = useAuthStore();

  const { data: today, isLoading: todayLoading, error: todayError } = useToday();
  const sentenceId = today?.sentenceId;

  const { data: status } = useGameStatus(sentenceId);
  const { data: history } = useGameHistory(sentenceId);
  const { state: anonState, addGuess: addAnonGuess } = useAnonymousGame(sentenceId);

  const guessMutation = useGuess();
  const { formatted: countdown, isExpired } = useCountdown(today?.expiresAt);

  const [inputError, setInputError] = useState<string | null>(null);
  const [rateLimited, setRateLimited] = useState(false);
  const [localCleared, setLocalCleared] = useState(false);
  const [isHelpOpen, setIsHelpOpen] = useState(() => {
    try {
      return !localStorage.getItem(HELP_SHOWN_KEY);
    } catch {
      return true;
    }
  });
  const retryRef = useRef(false);
  const rateLimitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [trackedSentenceId, setTrackedSentenceId] = useState(sentenceId);

  if (sentenceId !== trackedSentenceId) {
    setTrackedSentenceId(sentenceId);
    setLocalCleared(false);
    setInputError(null);
    setRateLimited(false);
  }

  const isCleared = isAuthenticated
    ? status?.gameStatus === "CLEARED" || localCleared
    : anonState.isCleared || localCleared;

  const displayGuesses = isAuthenticated ? (history?.guesses ?? []) : anonState.guesses;

  const attemptCount = isAuthenticated ? (status?.attemptCount ?? 0) : anonState.attemptCount;

  const bestSimilarity = isAuthenticated ? (status?.bestSimilarity ?? 0) : anonState.bestSimilarity;

  useEffect(() => {
    if (!isExpired || !today?.expiresAt) {
      return;
    }
    queryClient.invalidateQueries({ queryKey: ["game", "today"] });
  }, [isExpired, today?.expiresAt, queryClient]);

  useEffect(() => {
    if (!localCleared) {
      return;
    }
    const opts = { particleCount: 200, spread: 360, origin: { y: 0.5 } };
    confetti({ ...opts, origin: { x: 0.2, y: 0.5 } });
    confetti({ ...opts, origin: { x: 0.35, y: 0.5 } });
    confetti({ ...opts, origin: { x: 0.5, y: 0.5 } });
    confetti({ ...opts, origin: { x: 0.65, y: 0.5 } });
    confetti({ ...opts, origin: { x: 0.8, y: 0.5 } });

    const vol = getSoundVolume();
    if (vol > 0) {
      const audio = new Audio("/sounds/clear.mp3");
      audio.volume = vol;
      audio.play().catch(() => {});
    }
  }, [localCleared]);

  useEffect(
    () => () => {
      clearTimeout(rateLimitTimerRef.current ?? undefined);
    },
    [],
  );

  function appendGuessToCache(guessText: string, res: GuessResponse) {
    queryClient.setQueryData<HistoryResponse>(["game", "history", sentenceId], (old) => ({
      guesses: [
        ...(old?.guesses ?? []),
        {
          guessText,
          similarity: res.similarity,
          attemptNumber: (old?.guesses?.length ?? 0) + 1,
          createdAt: res.timestamp,
        },
      ],
    }));
    queryClient.invalidateQueries({ queryKey: ["game", "status", sentenceId] });
    queryClient.invalidateQueries({ queryKey: ["ranking"] });
  }

  function handleGuessError(err: unknown, sentenceId: string, guessText: string) {
    if (!(err instanceof ApiError)) {
      addToast("알 수 없는 오류가 발생했습니다.", "error");
      return;
    }

    switch (err.code) {
      case "RATE_LIMIT_EXCEEDED": {
        const seconds = err.retryAfter ?? 5;
        setRateLimited(true);
        clearTimeout(rateLimitTimerRef.current ?? undefined);
        rateLimitTimerRef.current = setTimeout(() => {
          setRateLimited(false);
          rateLimitTimerRef.current = null;
        }, seconds * 1000);
        addToast(`요청이 너무 많습니다. ${seconds}초 후 다시 시도해주세요.`, "error");
        break;
      }
      case "GAME_EXPIRED":
      case "SENTENCE_NOT_FOUND":
        addToast("새로운 문제를 불러옵니다.", "info");
        queryClient.removeQueries({ queryKey: ["game"] });
        break;
      case "CONCURRENT_MODIFICATION":
        if (!retryRef.current) {
          retryRef.current = true;
          guessMutation.mutate(
            { sentenceId, guessText },
            {
              onSuccess: (res) => {
                retryRef.current = false;
                if (isAuthenticated) {
                  appendGuessToCache(guessText, res);
                } else {
                  addAnonGuess(guessText, res.similarity, res.isCorrect);
                }
                if (res.isCorrect && !isCleared) {
                  setLocalCleared(true);
                }
              },
              onError: () => {
                retryRef.current = false;
                addToast("다시 시도해주세요.", "error");
              },
            },
          );
        } else {
          retryRef.current = false;
          addToast("다시 시도해주세요.", "error");
        }
        break;
      case "AI_SERVICE_UNAVAILABLE":
        addToast("AI 서버 점검 중입니다. 잠시 후 다시 시도해주세요.", "error");
        break;
      case "INVALID_GUESS_TEXT":
        setInputError(err.message);
        break;
      default:
        addToast(err.message, "error");
    }
  }

  function handleSubmit(text: string) {
    if (!sentenceId) {
      return;
    }
    setInputError(null);

    const existing = displayGuesses.find((g) => g.guessText === text);
    if (existing) {
      setInputError(`이미 추측한 문장입니다 (유사도: ${existing.similarity.toFixed(1)}%)`);
      return;
    }

    guessMutation.mutate(
      { sentenceId, guessText: text },
      {
        onSuccess: (res) => {
          if (isAuthenticated) {
            appendGuessToCache(text, res);
          } else {
            addAnonGuess(text, res.similarity, res.isCorrect);
          }
          if (res.isCorrect && !isCleared) {
            setLocalCleared(true);
          }
        },
        onError: (err) => handleGuessError(err, sentenceId, text),
      },
    );
  }

  if (todayLoading || authLoading) {
    return (
      <div className="max-w-2xl flex-1 min-w-0 space-y-6">
        <div className="h-10 w-48 bg-gray-200 dark:bg-gray-800 animate-pulse" />
        <div className="border-4 border-gray-200 dark:border-gray-800 p-6">
          <div className="h-8 w-full bg-gray-200 dark:bg-gray-800 animate-pulse" />
        </div>
      </div>
    );
  }

  if (todayError) {
    const message = todayError instanceof ApiError ? todayError.message : "문제를 불러올 수 없습니다.";
    return (
      <div className="max-w-2xl flex-1 min-w-0 space-y-6">
        <h1 className="text-4xl font-black">오늘의 문장</h1>
        <Card>
          <p className="text-lg font-bold text-red-500">{message}</p>
        </Card>
      </div>
    );
  }

  if (!today) {
    return null;
  }

  return (
    <div className="max-w-2xl flex-1 min-w-0 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-4xl font-black">오늘의 문장</h1>
        <div className="flex items-center gap-3">
          <span className="text-sm font-bold text-gray-600 dark:text-gray-400">
            {isExpired ? (
              "새 문장 준비 중..."
            ) : (
              <>
                다음 문장까지: <span className="tabular-nums">{countdown}</span>
              </>
            )}
          </span>
          <button
            type="button"
            onClick={() => setIsHelpOpen(true)}
            className="border-2 border-black dark:border-white bg-yellow-300 text-black px-2 py-0.5 text-xs font-black shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]"
            aria-label="플레이 방법"
          >
            플레이 방법
          </button>
        </div>
      </div>

      {today.yesterdaySentence && today.yesterdayDate && (
        <YesterdayAnswer sentence={today.yesterdaySentence} date={today.yesterdayDate} />
      )}

      <Card>
        <HintMask hintMask={today.hintMask} charCounts={today.charCounts} />
      </Card>

      {isCleared && <GameClearedCard attemptCount={attemptCount} bestSimilarity={bestSimilarity} />}

      <GuessInput
        onSubmit={handleSubmit}
        isLoading={guessMutation.isPending}
        disabled={rateLimited}
        error={inputError}
      />

      <GuessHistory guesses={displayGuesses} />

      <HelpModal
        isOpen={isHelpOpen}
        onClose={() => {
          try {
            localStorage.setItem(HELP_SHOWN_KEY, "true");
          } catch {
            // 스토리지 접근 불가 시 무시
          }
          setIsHelpOpen(false);
        }}
      />
    </div>
  );
}
