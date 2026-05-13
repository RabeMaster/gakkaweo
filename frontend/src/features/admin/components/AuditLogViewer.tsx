import { useState } from "react";
import { Button } from "@/shared/ui/Button";
import { useAuditLogs } from "@/features/admin/hooks/useAdminSystem";
import { useSortState } from "@/features/admin/hooks/useSortState";
import { SortableHeader } from "@/features/admin/components/SortableHeader";
import { AuditLogDetailDialog } from "@/features/admin/components/AuditLogDetailDialog";
import { AUDIT_ACTION_LABELS, getAuditActionLabel, getAuditTargetTypeLabel } from "@/features/admin/labels";
import type { AuditLog } from "@/features/admin/types";

function toKstDateFromIso(date: string): string {
  return `${date}T00:00:00+09:00`;
}

function toKstDateToIso(date: string): string {
  return `${date}T23:59:59.999+09:00`;
}

export function AuditLogViewer() {
  const [actionFilter, setActionFilter] = useState("");
  const [dateFromInput, setDateFromInput] = useState("");
  const [dateToInput, setDateToInput] = useState("");
  const [page, setPage] = useState(0);
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);
  const { sort, toggleSort } = useSortState();

  const dateFromIso = dateFromInput ? toKstDateFromIso(dateFromInput) : undefined;
  const dateToIso = dateToInput ? toKstDateToIso(dateToInput) : undefined;

  const { data, isLoading } = useAuditLogs(actionFilter || undefined, dateFromIso, dateToIso, sort, page, 20);

  function handleSortChange(field: string) {
    toggleSort(field);
    setPage(0);
  }

  return (
    <div className="mt-4 space-y-3">
      <div className="flex gap-2 items-center flex-wrap">
        <select
          value={actionFilter}
          onChange={(e) => {
            setActionFilter(e.target.value);
            setPage(0);
          }}
          className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-sm font-bold px-3 py-1.5 shadow-brutal-sm dark:[color-scheme:dark]"
        >
          <option value="">전체 액션</option>
          {Object.entries(AUDIT_ACTION_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <label className="flex items-center gap-1 text-xs font-bold">
          <span className="text-gray-600 dark:text-gray-400">시작</span>
          <input
            type="date"
            value={dateFromInput}
            max={dateToInput || undefined}
            onChange={(e) => {
              setDateFromInput(e.target.value);
              setPage(0);
            }}
            className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-sm font-bold px-2 py-1.5 shadow-brutal-sm dark:[color-scheme:dark]"
          />
        </label>
        <label className="flex items-center gap-1 text-xs font-bold">
          <span className="text-gray-600 dark:text-gray-400">종료</span>
          <input
            type="date"
            value={dateToInput}
            min={dateFromInput || undefined}
            onChange={(e) => {
              setDateToInput(e.target.value);
              setPage(0);
            }}
            className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-sm font-bold px-2 py-1.5 shadow-brutal-sm dark:[color-scheme:dark]"
          />
        </label>
        {(dateFromInput || dateToInput) && (
          <Button
            size="sm"
            variant="secondary"
            onClick={() => {
              setDateFromInput("");
              setDateToInput("");
              setPage(0);
            }}
          >
            날짜 초기화
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="font-bold text-gray-400 animate-pulse">로딩 중...</p>
      ) : data && data.content.length > 0 ? (
        <>
          <div className="border-2 border-black dark:border-white overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b-2 border-black dark:border-white bg-gray-100 dark:bg-gray-800">
                  <SortableHeader
                    field="createdAt"
                    label="시간"
                    align="left"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-2"
                  />
                  <th className="px-3 py-2 font-black text-left">관리자</th>
                  <SortableHeader
                    field="action"
                    label="액션"
                    align="left"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-2"
                  />
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
                      <span
                        className="inline-block px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 font-black text-[10px]"
                        title={log.action}
                      >
                        {getAuditActionLabel(log.action)}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-gray-600 dark:text-gray-400" title={log.targetType}>
                      {getAuditTargetTypeLabel(log.targetType)}
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
