import { useState } from "react";
import { Button } from "@/shared/ui/Button";
import { useSentences, useDeleteSentence } from "@/features/admin/hooks/useAdminSentences";
import { useSortState } from "@/features/admin/hooks/useSortState";
import { SortableHeader } from "@/features/admin/components/SortableHeader";
import { SentenceDetailDialog } from "@/features/admin/components/SentenceDetailDialog";
import { CsvUploadDialog } from "@/features/admin/components/CsvUploadDialog";
import { SimilarityTestDialog } from "@/features/admin/components/SimilarityTestDialog";
import { Pagination } from "@/features/admin/components/Pagination";
import type { SentenceResponse } from "@/features/admin/types";
import { useToastStore } from "@/shared/stores/useToastStore";
import { ApiError } from "@/shared/api/client";

function StatusBadge({ status }: { status: string }) {
  const color =
    status === "ACTIVE"
      ? "bg-green-400 text-black"
      : status === "USED"
        ? "bg-blue-300 text-black"
        : "bg-gray-300 dark:bg-gray-600 text-black dark:text-white";
  return (
    <span className={`inline-block px-2 py-0.5 text-xs font-black border-2 border-black dark:border-white ${color}`}>
      {status}
    </span>
  );
}

export function SentenceTab() {
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [page, setPage] = useState(0);
  const [editTarget, setEditTarget] = useState<SentenceResponse | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isCsvOpen, setIsCsvOpen] = useState(false);
  const [isSimilarityOpen, setIsSimilarityOpen] = useState(false);
  const { sort, toggleSort } = useSortState();

  const { data, isLoading } = useSentences(statusFilter || undefined, sort, page);
  const deleteMutation = useDeleteSentence();
  const { addToast } = useToastStore();

  function handleSortChange(field: string) {
    toggleSort(field);
    setPage(0);
  }

  function handleDelete(publicId: string) {
    if (!window.confirm("정말 삭제하시겠습니까?")) {
      return;
    }
    deleteMutation.mutate(publicId, {
      onSuccess: () => addToast("문장이 삭제되었습니다.", "success"),
      onError: (err) => addToast(err instanceof ApiError ? err.message : "삭제 실패", "error"),
    });
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-extrabold">문장 관리</h2>
        <div className="flex gap-2">
          <Button size="sm" onClick={() => setIsSimilarityOpen(true)}>
            유사도 테스트
          </Button>
          <Button size="sm" onClick={() => setIsCsvOpen(true)}>
            CSV 업로드
          </Button>
          <Button size="sm" onClick={() => setIsCreateOpen(true)}>
            문장 등록
          </Button>
        </div>
      </div>

      <div className="flex gap-2 items-center">
        <label className="text-sm font-bold">상태:</label>
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
          className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-sm font-bold px-3 py-1.5"
        >
          <option value="">전체</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="USED">USED</option>
          <option value="DISABLED">DISABLED</option>
        </select>
      </div>

      {isLoading ? (
        <p className="font-bold text-gray-400 animate-pulse py-8">로딩 중...</p>
      ) : data && data.sentences.length > 0 ? (
        <>
          <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b-4 border-black dark:border-white bg-gray-100 dark:bg-gray-800">
                  <SortableHeader
                    field="sentence"
                    label="문장"
                    align="left"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-4 py-3"
                  />
                  <SortableHeader
                    field="status"
                    label="상태"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-3 w-24"
                  />
                  <SortableHeader
                    field="usedAt"
                    label="출제일"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-3 w-28"
                  />
                  <SortableHeader
                    field="scheduledAt"
                    label="예약일"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-3 w-28"
                  />
                  <th className="text-center px-3 py-3 font-black w-36">액션</th>
                </tr>
              </thead>
              <tbody>
                {data.sentences.map((s) => (
                  <tr
                    key={s.publicId}
                    className="border-b-2 border-black/20 dark:border-white/20 hover:bg-yellow-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    <td className="px-4 py-3 font-medium truncate max-w-xs">{s.sentence}</td>
                    <td className="text-center px-3 py-3">
                      <StatusBadge status={s.status} />
                    </td>
                    <td className="text-center px-3 py-3 tabular-nums text-gray-600 dark:text-gray-400">
                      {s.usedAt ?? "-"}
                    </td>
                    <td className="text-center px-3 py-3 tabular-nums text-gray-600 dark:text-gray-400">
                      {s.scheduledAt ?? "-"}
                    </td>
                    <td className="text-center px-3 py-3">
                      <div className="flex gap-1 justify-center">
                        <Button size="sm" variant="secondary" onClick={() => setEditTarget(s)}>
                          편집
                        </Button>
                        {!s.usedAt && (
                          <Button
                            size="sm"
                            variant="danger"
                            onClick={() => handleDelete(s.publicId)}
                            isLoading={deleteMutation.isPending && deleteMutation.variables === s.publicId}
                          >
                            삭제
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <p className="text-gray-500 dark:text-gray-400 font-bold py-8 text-center">문장이 없습니다.</p>
      )}

      {(isCreateOpen || editTarget) && (
        <SentenceDetailDialog
          sentence={editTarget}
          onClose={() => {
            setEditTarget(null);
            setIsCreateOpen(false);
          }}
        />
      )}

      {isCsvOpen && <CsvUploadDialog onClose={() => setIsCsvOpen(false)} />}
      {isSimilarityOpen && <SimilarityTestDialog onClose={() => setIsSimilarityOpen(false)} />}
    </div>
  );
}
