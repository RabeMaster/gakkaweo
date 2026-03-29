import { useEffect, useState } from "react";
import { Button } from "@/shared/ui/Button";
import { Input } from "@/shared/ui/Input";
import { useTestSimilarity } from "@/features/admin/hooks/useAdminSentences";
import { getSimilarityColor } from "@/shared/utils/similarity";
import { useToastStore } from "@/shared/stores/useToastStore";
import { ApiError } from "@/shared/api/client";

interface SimilarityTestDialogProps {
  onClose: () => void;
}

export function SimilarityTestDialog({ onClose }: SimilarityTestDialogProps) {
  const [sentence, setSentence] = useState("");
  const [guessText, setGuessText] = useState("");
  const testMutation = useTestSimilarity();
  const { addToast } = useToastStore();

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        onClose();
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  function handleTest() {
    if (!sentence.trim() || !guessText.trim()) {
      return;
    }
    testMutation.mutate(
      { sentence: sentence.trim(), guessText: guessText.trim() },
      {
        onError: (err) => addToast(err instanceof ApiError ? err.message : "테스트 실패", "error"),
      },
    );
  }

  const result = testMutation.data;
  const color = result ? getSimilarityColor(Number(result.similarity)) : null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-md">
        <div className="px-6 py-5 border-b-4 border-black dark:border-white">
          <h2 className="text-xl font-black">유사도 테스트</h2>
        </div>

        <div className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-sm font-bold mb-1">정답 문장</label>
            <Input value={sentence} onChange={(e) => setSentence(e.target.value)} placeholder="정답 문장" />
          </div>
          <div>
            <label className="block text-sm font-bold mb-1">추측 문장</label>
            <Input value={guessText} onChange={(e) => setGuessText(e.target.value)} placeholder="추측 입력" />
          </div>

          {result && color && (
            <div
              className="border-4 border-black dark:border-white p-4 text-center"
              style={{ backgroundColor: color.bg }}
            >
              <p className="text-3xl font-black tabular-nums" style={{ color: color.text }}>
                {Number(result.similarity).toFixed(1)}%
              </p>
            </div>
          )}
        </div>

        <div className="flex gap-3 px-6 py-4 border-t-4 border-black dark:border-white">
          <Button variant="secondary" size="sm" className="flex-1" onClick={onClose}>
            닫기
          </Button>
          <Button size="sm" className="flex-1" onClick={handleTest} isLoading={testMutation.isPending}>
            테스트
          </Button>
        </div>
      </div>
    </div>
  );
}
