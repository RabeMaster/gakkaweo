import { useState } from "react";
import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";
import { Input } from "@/shared/ui/Input";
import {
  useCreateSentence,
  useUpdateSentence,
  useScheduleSentence,
  useUnscheduleSentence,
  useSentenceStats,
  useCheckDuplicate,
  useEmergencyReplace,
} from "@/features/admin/hooks/useAdminSentences";
import type { SentenceResponse } from "@/features/admin/types";
import { useToastStore } from "@/shared/stores/useToastStore";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { ApiError } from "@/shared/api/client";

interface SentenceDetailDialogProps {
  sentence: SentenceResponse | null;
  onClose: () => void;
}

export function SentenceDetailDialog({ sentence, onClose }: SentenceDetailDialogProps) {
  const isEdit = !!sentence;
  const [text, setText] = useState(sentence?.sentence ?? "");
  const [scheduleDate, setScheduleDate] = useState(sentence?.scheduledAt ?? "");

  const createMutation = useCreateSentence();
  const updateMutation = useUpdateSentence();
  const scheduleMutation = useScheduleSentence();
  const unscheduleMutation = useUnscheduleSentence();
  const duplicateCheck = useCheckDuplicate();
  const emergencyReplace = useEmergencyReplace();
  const { data: stats } = useSentenceStats(sentence?.publicId ?? null);
  const { addToast } = useToastStore();
  const isSuperAdmin = useAuthStore((s) => s.user?.role === "SUPERADMIN");

  const isSaving = createMutation.isPending || updateMutation.isPending;

  function handleSave() {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }

    if (isEdit) {
      updateMutation.mutate(
        { publicId: sentence.publicId, sentence: trimmed },
        {
          onSuccess: () => {
            addToast("수정되었습니다.", "success");
            onClose();
          },
          onError: (err) => addToast(err instanceof ApiError ? err.message : "수정 실패", "error"),
        },
      );
    } else {
      createMutation.mutate(trimmed, {
        onSuccess: () => {
          addToast("등록되었습니다.", "success");
          onClose();
        },
        onError: (err) => addToast(err instanceof ApiError ? err.message : "등록 실패", "error"),
      });
    }
  }

  function handleDuplicateCheck() {
    const trimmed = text.trim();
    if (!trimmed) {
      return;
    }
    duplicateCheck.mutate(trimmed, {
      onSuccess: (res) => {
        if (res.hasDuplicate) {
          const top = res.similarEntries[0];
          addToast(`유사 문장 발견: "${top.sentence}" (${Number(top.similarity).toFixed(1)}%)`, "error");
        } else {
          addToast("중복 문장이 없습니다.", "success");
        }
      },
      onError: (err) => addToast(err instanceof ApiError ? err.message : "중복 검사 실패", "error"),
    });
  }

  function handleSchedule() {
    if (!sentence || !scheduleDate) {
      return;
    }
    scheduleMutation.mutate(
      { publicId: sentence.publicId, date: scheduleDate },
      {
        onSuccess: () => addToast(`${scheduleDate}에 예약되었습니다.`, "success"),
        onError: (err) => addToast(err instanceof ApiError ? err.message : "예약 실패", "error"),
      },
    );
  }

  function handleUnschedule() {
    if (!sentence) {
      return;
    }
    unscheduleMutation.mutate(sentence.publicId, {
      onSuccess: () => {
        addToast("예약이 해제되었습니다.", "success");
        setScheduleDate("");
      },
      onError: (err) => addToast(err instanceof ApiError ? err.message : "해제 실패", "error"),
    });
  }

  function handleEmergencyReplace() {
    if (!sentence) {
      return;
    }
    if (!window.confirm("긴급 교체를 진행하시겠습니까? 진행 중인 게임 세션이 만료됩니다.")) {
      return;
    }
    emergencyReplace.mutate(
      { newSentencePublicId: sentence.publicId, returnOldToPool: true },
      {
        onSuccess: () => {
          addToast("긴급 교체 완료", "success");
          onClose();
        },
        onError: (err) => addToast(err instanceof ApiError ? err.message : "교체 실패", "error"),
      },
    );
  }

  return (
    <Dialog
      onClose={onClose}
      title={isEdit ? "문장 편집" : "문장 등록"}
      maxWidth="max-w-lg"
      disableClose={isSaving}
      footer={
        <>
          <Button variant="secondary" size="sm" className="flex-1" onClick={onClose}>
            닫기
          </Button>
          <Button size="sm" className="flex-1" onClick={handleSave} isLoading={isSaving}>
            {isEdit ? "수정" : "등록"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-bold mb-1">문장</label>
          <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="문장을 입력하세요" />
        </div>

        <div className="flex gap-2">
          <Button size="sm" variant="secondary" onClick={handleDuplicateCheck} isLoading={duplicateCheck.isPending}>
            중복 검사
          </Button>
        </div>

        {isEdit && (
          <>
            <div className="border-t-2 border-black/20 dark:border-white/20 pt-4">
              <label className="block text-sm font-bold mb-1">출제 예약</label>
              <div className="flex gap-2 items-center">
                <input
                  type="date"
                  value={scheduleDate}
                  onChange={(e) => setScheduleDate(e.target.value)}
                  className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 px-3 py-1.5 text-sm font-bold shadow-brutal-sm dark:[color-scheme:dark]"
                />
                <Button size="sm" onClick={handleSchedule} isLoading={scheduleMutation.isPending}>
                  예약
                </Button>
                {sentence.scheduledAt && (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={handleUnschedule}
                    isLoading={unscheduleMutation.isPending}
                  >
                    해제
                  </Button>
                )}
              </div>
            </div>

            {stats && (
              <div className="border-t-2 border-black/20 dark:border-white/20 pt-4">
                <p className="text-sm font-bold mb-2">통계</p>
                <div className="grid grid-cols-3 gap-3 text-center text-sm">
                  <div className="border-2 border-black dark:border-white p-2">
                    <p className="text-xs text-gray-500 dark:text-gray-400 font-bold">세션</p>
                    <p className="font-black tabular-nums">{stats.totalSessions}</p>
                  </div>
                  <div className="border-2 border-black dark:border-white p-2">
                    <p className="text-xs text-gray-500 dark:text-gray-400 font-bold">클리어율</p>
                    <p className="font-black tabular-nums">{stats.clearRate.toFixed(1)}%</p>
                  </div>
                  <div className="border-2 border-black dark:border-white p-2">
                    <p className="text-xs text-gray-500 dark:text-gray-400 font-bold">평균 시도</p>
                    <p className="font-black tabular-nums">{stats.avgAttemptCount.toFixed(1)}</p>
                  </div>
                </div>
              </div>
            )}

            {!sentence.usedAt && sentence.status === "ACTIVE" && (
              <div className="border-t-2 border-black/20 dark:border-white/20 pt-4 space-y-2">
                <Button
                  size="sm"
                  variant="danger"
                  onClick={handleEmergencyReplace}
                  disabled={!isSuperAdmin}
                  isLoading={emergencyReplace.isPending}
                >
                  긴급 교체 (이 문장으로)
                </Button>
                {!isSuperAdmin && (
                  <p className="text-xs font-bold text-amber-600 dark:text-amber-400">
                    SUPERADMIN만 긴급 교체를 수행할 수 있습니다.
                  </p>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </Dialog>
  );
}
