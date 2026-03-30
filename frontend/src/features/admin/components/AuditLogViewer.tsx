import { useEffect, useState } from "react";
import { Button } from "@/shared/ui/Button";
import { useAuditLogs } from "@/features/admin/hooks/useAdminSystem";
import type { AuditLog } from "@/features/admin/types";

function AuditLogDetailDialog({ log, onClose }: { log: AuditLog; onClose: () => void }) {
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-md">
        <div className="px-6 py-5 border-b-4 border-black dark:border-white flex items-center justify-between">
          <h2 className="text-xl font-black">감사 로그 상세</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="text-2xl font-black leading-none hover:text-red-500"
          >
            &times;
          </button>
        </div>
        <div className="px-6 py-5 space-y-3 text-sm">
          <div>
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400">시간</p>
            <p className="font-bold tabular-nums">{new Date(log.createdAt).toLocaleString("ko-KR")}</p>
          </div>
          <div>
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400">관리자</p>
            <p className="font-bold">{log.adminNickname}</p>
          </div>
          <div>
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400">액션</p>
            <p>
              <span className="inline-block px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 font-black text-xs">
                {log.action}
              </span>
            </p>
          </div>
          <div>
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400">대상</p>
            <p className="font-medium break-all">
              {log.targetType}
              {log.targetId ? `: ${log.targetId}` : ""}
            </p>
          </div>
          <div>
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400">상세</p>
            <p className="font-medium break-all whitespace-pre-wrap">{log.detail ?? "-"}</p>
          </div>
          <div>
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400">IP</p>
            <p className="font-medium tabular-nums">{log.ipAddress ?? "-"}</p>
          </div>
        </div>
        <div className="px-6 py-4 border-t-4 border-black dark:border-white">
          <Button variant="secondary" size="sm" className="w-full" onClick={onClose}>
            닫기
          </Button>
        </div>
      </div>
    </div>
  );
}

export function AuditLogViewer() {
  const [actionFilter, setActionFilter] = useState("");
  const [page, setPage] = useState(0);
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  const { data, isLoading } = useAuditLogs(actionFilter || undefined, undefined, undefined, page, 20);

  return (
    <div className="mt-4 space-y-3">
      <div className="flex gap-2 items-center">
        <select
          value={actionFilter}
          onChange={(e) => {
            setActionFilter(e.target.value);
            setPage(0);
          }}
          className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-sm font-bold px-3 py-1.5"
        >
          <option value="">전체 액션</option>
          <option value="SENTENCE_CREATE">문장 생성</option>
          <option value="SENTENCE_UPDATE">문장 수정</option>
          <option value="SENTENCE_DELETE">문장 삭제</option>
          <option value="CSV_UPLOAD">CSV 업로드</option>
          <option value="SENTENCE_SCHEDULE">문장 예약</option>
          <option value="EMERGENCY_REPLACE">긴급 교체</option>
          <option value="ROLE_CHANGE">역할 변경</option>
          <option value="USER_BAN">사용자 차단</option>
          <option value="USER_UNBAN">차단 해제</option>
          <option value="USER_FORCE_DELETE">강제 탈퇴</option>
          <option value="USER_FORCE_NICKNAME">닉네임 변경</option>
          <option value="USER_FORCE_PROFILE_DELETE">프로필 삭제</option>
          <option value="ANNOUNCEMENT_CREATE">공지 생성</option>
          <option value="ANNOUNCEMENT_UPDATE">공지 수정</option>
          <option value="ANNOUNCEMENT_DELETE">공지 삭제</option>
          <option value="RANKING_CACHE_RESET">캐시 리셋</option>
          <option value="RATE_LIMIT_RESET">Rate Limit 리셋</option>
        </select>
      </div>

      {isLoading ? (
        <p className="font-bold text-gray-400 animate-pulse">로딩 중...</p>
      ) : data && data.content.length > 0 ? (
        <>
          <div className="border-2 border-black dark:border-white overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b-2 border-black dark:border-white bg-gray-100 dark:bg-gray-800">
                  <th className="px-3 py-2 font-black text-left">시간</th>
                  <th className="px-3 py-2 font-black text-left">관리자</th>
                  <th className="px-3 py-2 font-black text-left">액션</th>
                  <th className="px-3 py-2 font-black text-left">대상</th>
                  <th className="px-3 py-2 font-black text-left">상세</th>
                  <th className="px-3 py-2 font-black text-center w-16">보기</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((log) => (
                  <tr key={log.id} className="border-b border-black/10 dark:border-white/10">
                    <td className="px-3 py-2 tabular-nums whitespace-nowrap">
                      {new Date(log.createdAt).toLocaleString("ko-KR", {
                        month: "2-digit",
                        day: "2-digit",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td className="px-3 py-2 font-bold">{log.adminNickname}</td>
                    <td className="px-3 py-2">
                      <span className="inline-block px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 font-black text-[10px]">
                        {log.action}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-gray-600 dark:text-gray-400">
                      {log.targetType}
                      {log.targetId ? `: ${log.targetId.slice(0, 8)}...` : ""}
                    </td>
                    <td className="px-3 py-2 text-gray-500 dark:text-gray-400 truncate max-w-[200px]">
                      {log.detail ?? "-"}
                    </td>
                    <td className="px-3 py-2 text-center">
                      <button
                        type="button"
                        onClick={() => setSelectedLog(log)}
                        className="text-indigo-600 dark:text-indigo-400 font-black text-xs hover:underline"
                      >
                        상세
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="flex items-center gap-2 justify-center">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                이전
              </Button>
              <span className="text-xs font-bold tabular-nums">
                {page + 1} / {data.totalPages}
              </span>
              <Button
                size="sm"
                variant="secondary"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                다음
              </Button>
            </div>
          )}
        </>
      ) : (
        <p className="text-gray-500 dark:text-gray-400 font-bold text-sm">로그가 없습니다.</p>
      )}

      {selectedLog && <AuditLogDetailDialog log={selectedLog} onClose={() => setSelectedLog(null)} />}
    </div>
  );
}
