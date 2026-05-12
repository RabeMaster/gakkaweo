import { useState } from "react";
import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";
import { Input } from "@/shared/ui/Input";
import { useCreateAnnouncement, useUpdateAnnouncement } from "@/features/admin/hooks/useAdminSystem";
import { ANNOUNCEMENT_TYPE_LABELS, getAnnouncementTypeColor } from "@/shared/config/announcement";
import type { AnnouncementResponse } from "@/features/admin/types";
import { useToastStore } from "@/shared/stores/useToastStore";
import { ApiError } from "@/shared/api/client";

interface AnnouncementDialogProps {
  announcement: AnnouncementResponse | null;
  onClose: () => void;
}

function toLocalDatetimeValue(iso: string): string {
  const d = new Date(iso);
  const offset = d.getTimezoneOffset();
  const local = new Date(d.getTime() - offset * 60 * 1000);
  return local.toISOString().slice(0, 16);
}

export function AnnouncementDialog({ announcement, onClose }: AnnouncementDialogProps) {
  const isEdit = !!announcement;
  const [title, setTitle] = useState(announcement?.title ?? "");
  const [content, setContent] = useState(announcement?.content ?? "");
  const [type, setType] = useState<"INFO" | "MAINTENANCE" | "WARNING">(announcement?.type ?? "INFO");
  const [active, setActive] = useState(announcement?.active ?? true);
  const [startsAt, setStartsAt] = useState(announcement ? toLocalDatetimeValue(announcement.startsAt) : "");
  const [endsAt, setEndsAt] = useState(announcement?.endsAt ? toLocalDatetimeValue(announcement.endsAt) : "");

  const createMutation = useCreateAnnouncement();
  const updateMutation = useUpdateAnnouncement();
  const { addToast } = useToastStore();

  const isSaving = createMutation.isPending || updateMutation.isPending;

  function handleSave() {
    if (!title.trim() || !startsAt) {
      addToast("제목과 시작일은 필수입니다.", "error");
      return;
    }

    const startsAtIso = new Date(startsAt).toISOString();
    const endsAtIso = endsAt ? new Date(endsAt).toISOString() : undefined;

    if (isEdit) {
      updateMutation.mutate(
        {
          id: announcement.id,
          title: title.trim(),
          content: content.trim() || undefined,
          type,
          active,
          startsAt: startsAtIso,
          endsAt: endsAtIso,
        },
        {
          onSuccess: () => {
            addToast("수정되었습니다.", "success");
            onClose();
          },
          onError: (err) => addToast(err instanceof ApiError ? err.message : "수정 실패", "error"),
        },
      );
    } else {
      createMutation.mutate(
        { title: title.trim(), content: content.trim() || undefined, type, startsAt: startsAtIso, endsAt: endsAtIso },
        {
          onSuccess: () => {
            addToast("등록되었습니다.", "success");
            onClose();
          },
          onError: (err) => addToast(err instanceof ApiError ? err.message : "등록 실패", "error"),
        },
      );
    }
  }

  return (
    <Dialog
      onClose={onClose}
      title={isEdit ? "공지 수정" : "공지 등록"}
      maxWidth="max-w-md"
      disableClose={isSaving}
      footer={
        <>
          <Button variant="secondary" size="sm" className="flex-1" onClick={onClose}>
            취소
          </Button>
          <Button size="sm" className="flex-1" onClick={handleSave} isLoading={isSaving}>
            {isEdit ? "수정" : "등록"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-bold mb-1">제목</label>
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="공지 제목" />
        </div>

        <div>
          <label className="block text-sm font-bold mb-1">내용 (선택)</label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="공지 내용"
            rows={3}
            className="w-full border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white px-4 py-3 font-medium text-sm focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400"
          />
        </div>

        <div className="flex gap-4 items-end">
          <div>
            <label className="block text-sm font-bold mb-1">유형</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as "INFO" | "MAINTENANCE" | "WARNING")}
              className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-sm font-bold px-3 py-1.5 shadow-brutal-sm dark:[color-scheme:dark]"
            >
              {Object.entries(ANNOUNCEMENT_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div
            className={`border-4 border-black dark:border-white px-3 py-1 text-xs font-black ${getAnnouncementTypeColor(type)}`}
          >
            미리보기
          </div>

          {isEdit && (
            <div>
              <label className="block text-sm font-bold mb-1">활성</label>
              <label className="flex items-center gap-2 text-sm font-bold cursor-pointer">
                <input
                  type="checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="w-5 h-5 border-2 border-black dark:border-white accent-yellow-400"
                />
                {active ? "활성" : "비활성"}
              </label>
            </div>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-bold mb-1">시작</label>
            <input
              type="datetime-local"
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
              className="w-full border-4 border-black dark:border-white bg-white dark:bg-gray-900 px-3 py-1.5 text-sm font-bold shadow-brutal-sm dark:[color-scheme:dark]"
            />
          </div>
          <div>
            <label className="block text-sm font-bold mb-1">종료 (선택)</label>
            <input
              type="datetime-local"
              value={endsAt}
              onChange={(e) => setEndsAt(e.target.value)}
              className="w-full border-4 border-black dark:border-white bg-white dark:bg-gray-900 px-3 py-1.5 text-sm font-bold shadow-brutal-sm dark:[color-scheme:dark]"
            />
          </div>
        </div>
      </div>
    </Dialog>
  );
}
