import { useRef, useState } from "react";
import { Button } from "@/shared/ui/Button";
import { Input } from "@/shared/ui/Input";
import { getSimilarityColor } from "@/shared/utils/similarity";

interface GuessInputProps {
  onSubmit: (text: string) => void;
  isLoading: boolean;
  disabled?: boolean;
  error?: string | null;
  result?: { guessText: string; similarity: number } | null;
}

export function GuessInput({ onSubmit, isLoading, disabled = false, error = null, result = null }: GuessInputProps) {
  const [text, setText] = useState("");
  const wrapperRef = useRef<HTMLDivElement>(null);
  const isComposingRef = useRef(false);

  function focusInput() {
    setTimeout(() => wrapperRef.current?.querySelector<HTMLInputElement>("input")?.focus(), 0);
  }

  function handleSubmit() {
    if (isLoading || disabled) {
      return;
    }
    const trimmed = text.trim();
    if (!trimmed || trimmed.length < 2 || trimmed.length > 200) {
      return;
    }
    onSubmit(trimmed);
    setText("");
    focusInput();
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key !== "Enter") {
      return;
    }
    if (isComposingRef.current) {
      return;
    }
    e.preventDefault();
    handleSubmit();
  }

  return (
    <div ref={wrapperRef} className="space-y-2">
      <div className="flex gap-3">
        <Input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          onCompositionStart={() => {
            isComposingRef.current = true;
          }}
          onCompositionEnd={() => {
            isComposingRef.current = false;
          }}
          placeholder="문장을 추측해보세요..."
          disabled={disabled}
          maxLength={200}
        />
        <Button
          onClick={handleSubmit}
          isLoading={isLoading}
          disabled={disabled || isLoading || text.trim().length < 2}
          className="shrink-0"
        >
          제출
        </Button>
      </div>
      {error && <p className="text-sm font-bold text-red-500">{error}</p>}
      {!error && result && (
        <p className="text-sm font-bold text-gray-700 dark:text-gray-300">
          {result.guessText}{" "}
          <span style={{ color: getSimilarityColor(result.similarity).bg }}>
            (유사도: {result.similarity.toFixed(1)}%)
          </span>
        </p>
      )}
    </div>
  );
}
