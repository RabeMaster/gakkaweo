import { useState } from "react";
import { Button } from "@/shared/ui/Button";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import {
  useAnnouncements,
  useSystemStatus,
  useResetRankingCache,
  useResetRateLimit,
} from "@/features/admin/hooks/useAdminSystem";
import { AnnouncementDialog } from "@/features/admin/components/AnnouncementDialog";
import { AuditLogViewer } from "@/features/admin/components/AuditLogViewer";
import { useDeleteAnnouncement } from "@/features/admin/hooks/useAdminSystem";
import { getAnnouncementTypeColor, getAnnouncementTypeLabel } from "@/shared/config/announcement";
import type { AnnouncementResponse } from "@/features/admin/types";
import { useToastStore } from "@/shared/stores/useToastStore";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { isSuperAdmin } from "@/shared/utils/role";
import { ApiError } from "@/shared/api/client";

function StatusIndicator({ healthy, label }: { healthy: boolean; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <div className={`w-3 h-3 border-2 border-black dark:border-white ${healthy ? "bg-green-400" : "bg-red-500"}`} />
      <span className="text-sm font-bold">{label}</span>
    </div>
  );
}

function AnnouncementTypeBadge({ type }: { type: string }) {
  return (
    <span
      className={`inline-block px-2 py-0.5 text-xs font-black border-2 border-black dark:border-white ${getAnnouncementTypeColor(type)}`}
    >
      {getAnnouncementTypeLabel(type)}
    </span>
  );
}

export function SystemTab() {
  const { data: status } = useSystemStatus();
  const { data: announcements } = useAnnouncements();
  const resetCache = useResetRankingCache();
  const resetRate = useResetRateLimit();
  const deleteMutation = useDeleteAnnouncement();
  const { addToast } = useToastStore();
  const superAdmin = isSuperAdmin(useAuthStore((s) => s.user?.role));

  const [announcementEdit, setAnnouncementEdit] = useState<AnnouncementResponse | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [showAuditLog, setShowAuditLog] = useState(false);
  const [confirmState, setConfirmState] = useState<{
    action: () => void;
    title: string;
    message: string;
    confirmLabel: string;
  } | null>(null);

  function handleResetCache() {
    setConfirmState({
      action: () =>
        resetCache.mutate(undefined, {
          onSuccess: () => addToast("랭킹 캐시가 리셋되었습니다.", "success"),
          onError: (err) => addToast(err instanceof ApiError ? err.message : "리셋 실패", "error"),
        }),
      title: "캐시 리셋",
      message: "랭킹 캐시를 리셋하시겠습니까?",
      confirmLabel: "리셋",
    });
  }

  function handleResetRate() {
    setConfirmState({
      action: () =>
        resetRate.mutate(undefined, {
          onSuccess: () => addToast("Rate Limit이 초기화되었습니다.", "success"),
          onError: (err) => addToast(err instanceof ApiError ? err.message : "초기화 실패", "error"),
        }),
      title: "Rate Limit 초기화",
      message: "Rate Limit을 초기화하시겠습니까?",
      confirmLabel: "초기화",
    });
  }

  function handleDeleteAnnouncement(id: number) {
    setConfirmState({
      action: () =>
        deleteMutation.mutate(id, {
          onSuccess: () => addToast("공지가 삭제되었습니다.", "success"),
          onError: (err) => addToast(err instanceof ApiError ? err.message : "삭제 실패", "error"),
        }),
      title: "공지 삭제",
      message: "이 공지를 삭제하시겠습니까?",
      confirmLabel: "삭제",
    });
  }

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-extrabold">시스템 관리</h2>

      {/* 시스템 상태 */}
      {status && (
        <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal p-5">
          <h3 className="text-lg font-black mb-4">시스템 상태</h3>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <StatusIndicator
                healthy={status.aiServiceHealthy}
                label={`AI 서비스 (${status.aiServiceResponseMs}ms)`}
              />
              <StatusIndicator healthy={status.redisHealthy} label="Redis" />
            </div>
            <div className="space-y-1 text-sm font-medium text-gray-600 dark:text-gray-400">
              <p>
                SSE 연결: <span className="font-black text-black dark:text-white">{status.sseConnectionCount}</span>
              </p>
              <p>
                전체 회원: <span className="font-black text-black dark:text-white">{status.totalMembers}</span>
              </p>
              <p>
                전체 문장: <span className="font-black text-black dark:text-white">{status.totalSentences}</span>{" "}
                (미사용: {status.unusedSentences})
              </p>
            </div>
          </div>
          <div className="space-y-2 mt-4">
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={handleResetCache}
                disabled={!superAdmin}
                isLoading={resetCache.isPending}
              >
                랭킹 캐시 리셋
              </Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={handleResetRate}
                disabled={!superAdmin}
                isLoading={resetRate.isPending}
              >
                Rate Limit 초기화
              </Button>
            </div>
            {!superAdmin && (
              <p className="text-xs font-bold text-amber-600 dark:text-amber-400">
                SUPERADMIN만 시스템 리셋을 수행할 수 있습니다.
              </p>
            )}
          </div>
        </div>
      )}

      {/* 공지 관리 */}
      <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-black">공지 관리</h3>
          <Button size="sm" onClick={() => setIsCreateOpen(true)}>
            공지 등록
          </Button>
        </div>

        {announcements && announcements.length > 0 ? (
          <div className="space-y-2">
            {announcements.map((a) => (
              <div
                key={a.id}
                className={[
                  "border-2 border-black dark:border-white p-3 flex items-start gap-3",
                  a.active ? "" : "opacity-50",
                ].join(" ")}
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <AnnouncementTypeBadge type={a.type} />
                    <span className="font-black text-sm truncate">{a.title}</span>
                    {!a.active && <span className="text-xs font-bold text-gray-400">(비활성)</span>}
                  </div>
                  {a.content && (
                    <p className="text-xs text-gray-600 dark:text-gray-400 font-medium truncate">{a.content}</p>
                  )}
                  <p className="text-[10px] text-gray-400 font-medium mt-1">
                    {new Date(a.startsAt).toLocaleString("ko-KR")}
                    {a.endsAt ? ` ~ ${new Date(a.endsAt).toLocaleString("ko-KR")}` : " ~ 무기한"}
                  </p>
                </div>
                <div className="flex gap-1 shrink-0">
                  <Button size="sm" variant="secondary" onClick={() => setAnnouncementEdit(a)}>
                    편집
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={() => handleDeleteAnnouncement(a.id)}
                    isLoading={deleteMutation.isPending && deleteMutation.variables === a.id}
                  >
                    삭제
                  </Button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-gray-500 dark:text-gray-400 font-bold text-sm">공지가 없습니다.</p>
        )}
      </div>

      {/* 감사 로그 */}
      <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal p-5">
        <button
          type="button"
          onClick={() => setShowAuditLog(!showAuditLog)}
          className="text-lg font-black text-indigo-600 dark:text-indigo-400 hover:underline underline-offset-4 transition-colors"
        >
          {showAuditLog ? "▾ 감사 로그 숨기기" : "▸ 감사 로그 보기"}
        </button>
        {showAuditLog && <AuditLogViewer />}
      </div>

      {(isCreateOpen || announcementEdit) && (
        <AnnouncementDialog
          announcement={announcementEdit}
          onClose={() => {
            setAnnouncementEdit(null);
            setIsCreateOpen(false);
          }}
        />
      )}

      {confirmState && (
        <ConfirmDialog
          isOpen
          onClose={() => setConfirmState(null)}
          onConfirm={() => {
            confirmState.action();
            setConfirmState(null);
          }}
          title={confirmState.title}
          message={confirmState.message}
          confirmLabel={confirmState.confirmLabel}
        />
      )}
    </div>
  );
}
