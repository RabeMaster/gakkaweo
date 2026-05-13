import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import type { HintEntry } from "@/shared/api/types";
import { Card } from "@/shared/ui/Card";
import { SimilarityBadge } from "@/shared/ui/SimilarityBadge";

interface HintPanelProps {
  hints: HintEntry[];
  isLoading: boolean;
  bestSimilarity: number;
  isAuthenticated: boolean;
}

function SkeletonRow() {
  return <div className="h-8 bg-gray-200 dark:bg-gray-700 animate-pulse" />;
}

function HintRow({ hint, isLast }: { hint: HintEntry; isLast: boolean }) {
  const textRef = useRef<HTMLSpanElement>(null);
  const [isTruncated, setIsTruncated] = useState(false);
  const [isTooltipOpen, setIsTooltipOpen] = useState(false);

  useEffect(() => {
    const el = textRef.current;
    if (!el) {
      return;
    }
    setIsTruncated(el.scrollHeight > el.clientHeight);
  }, [hint.guessText]);

  return (
    <div
      className={[
        "relative flex items-start justify-between gap-2 py-2",
        isLast ? "" : "border-b-2 border-black/20 dark:border-white/20",
        isTruncated ? "cursor-pointer" : "",
      ].join(" ")}
      role={isTruncated ? "button" : undefined}
      tabIndex={isTruncated ? 0 : undefined}
      onClick={() => {
        if (isTruncated) {
          setIsTooltipOpen((prev) => !prev);
        }
      }}
      onKeyDown={(e: KeyboardEvent) => {
        if (isTruncated && (e.key === "Enter" || e.key === " ")) {
          e.preventDefault();
          setIsTooltipOpen((prev) => !prev);
        }
      }}
      onMouseEnter={() => {
        if (isTruncated) {
          setIsTooltipOpen(true);
        }
      }}
      onMouseLeave={() => setIsTooltipOpen(false)}
    >
      <span ref={textRef} className="text-sm font-medium line-clamp-2 break-all">
        {hint.guessText}
      </span>
      <span className="shrink-0">
        <SimilarityBadge similarity={hint.similarity} />
      </span>
      {isTruncated && isTooltipOpen && (
        <div
          role="tooltip"
          className="absolute z-20 inset-x-0 top-full mt-1 border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal-sm px-3 py-2 text-sm font-medium break-all"
        >
          {hint.guessText}
        </div>
      )}
    </div>
  );
}

function HintList({ hints }: { hints: HintEntry[] }) {
  return (
    <>
      {hints.map((hint, i) => (
        <HintRow key={hint.guessText} hint={hint} isLast={i === hints.length - 1} />
      ))}
    </>
  );
}

export function HintPanel({ hints, isLoading, bestSimilarity, isAuthenticated }: HintPanelProps) {
  const isLocked = bestSimilarity < 60;
  const [revealed, setRevealed] = useState(false);

  function renderContent() {
    if (!isAuthenticated) {
      return (
        <p className="text-sm font-bold text-gray-500 dark:text-gray-400 py-2">
          로그인하면 다른 플레이어의 추측을 힌트로 볼 수 있습니다
        </p>
      );
    }

    if (isLocked) {
      return (
        <div className="py-2 space-y-1.5">
          <p className="text-sm font-bold text-gray-500 dark:text-gray-400">유사도 60% 이상 달성 시 볼 수 있습니다</p>
          <p className="text-xs font-medium text-gray-400 dark:text-gray-500">
            내 최고 유사도보다 낮은 추측만 표시됩니다
          </p>
          <p className="text-xs font-medium text-gray-400 dark:text-gray-500">
            단, 90% 이상의 추측은 표시되지 않습니다
          </p>
        </div>
      );
    }

    if (isLoading) {
      return (
        <div className="space-y-2">
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </div>
      );
    }

    if (hints.length === 0) {
      return (
        <p className="text-sm font-bold text-gray-500 dark:text-gray-400 py-2">아직 다른 플레이어의 추측이 없습니다</p>
      );
    }

    if (!revealed) {
      return (
        <button type="button" onClick={() => setRevealed(true)} className="relative w-full text-left">
          <div className="select-none blur-sm pointer-events-none" aria-hidden="true">
            <HintList hints={hints} />
          </div>
          <div className="absolute inset-0 flex items-center justify-center">
            <span className="text-sm font-black bg-white dark:bg-gray-900 border-2 border-black dark:border-white px-3 py-1.5">
              클릭하여 힌트 보기
            </span>
          </div>
        </button>
      );
    }

    return <HintList hints={hints} />;
  }

  return (
    <Card className="!p-4 space-y-3">
      <h2 className="text-lg font-extrabold">다른 플레이어의 추측</h2>
      {renderContent()}
    </Card>
  );
}
