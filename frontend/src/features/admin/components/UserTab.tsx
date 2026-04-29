import { useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/shared/ui/Button";
import { Input } from "@/shared/ui/Input";
import { useUsers } from "@/features/admin/hooks/useAdminUsers";
import { useSortState } from "@/features/admin/hooks/useSortState";
import { SortableHeader } from "@/features/admin/components/SortableHeader";
import { UserDetailDialog } from "@/features/admin/components/UserDetailDialog";
import { Pagination } from "@/features/admin/components/Pagination";
import { resolveProfileUrl } from "@/shared/utils/url";

function RoleBadge({ role }: { role: string }) {
  const color =
    role === "SUPERADMIN"
      ? "bg-purple-400 text-black"
      : role === "ADMIN"
        ? "bg-red-400 text-black"
        : "bg-blue-300 text-black";
  const label = role === "SUPERADMIN" ? "SUPERADMIN" : role === "ADMIN" ? "ADMIN" : "USER";
  return (
    <span className={`inline-block px-2 py-0.5 text-xs font-black border-2 border-black dark:border-white ${color}`}>
      {label}
    </span>
  );
}

function BannedBadge() {
  return (
    <span className="inline-block px-2 py-0.5 text-xs font-black border-2 border-black dark:border-white bg-gray-800 text-white dark:bg-gray-200 dark:text-black">
      차단
    </span>
  );
}

export function UserTab() {
  const [search, setSearch] = useState("");
  const [submittedSearch, setSubmittedSearch] = useState("");
  const [bannedFilter, setBannedFilter] = useState<boolean | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const { sort, toggleSort } = useSortState();

  const { data, isLoading } = useUsers(submittedSearch || undefined, bannedFilter, sort, page);

  function handleSearch(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSubmittedSearch(search);
    setPage(0);
  }

  function handleSortChange(field: string) {
    toggleSort(field);
    setPage(0);
  }

  return (
    <div className="space-y-5">
      <h2 className="text-2xl font-extrabold">사용자 관리</h2>

      <form onSubmit={handleSearch} className="flex gap-2 items-center">
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="닉네임 검색"
          className="max-w-xs !py-1.5 !text-sm"
        />
        <select
          value={bannedFilter === undefined ? "" : String(bannedFilter)}
          onChange={(e) => {
            const val = e.target.value;
            setBannedFilter(val === "" ? undefined : val === "true");
            setPage(0);
          }}
          className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-sm font-bold px-3 py-1.5"
        >
          <option value="">전체</option>
          <option value="true">차단됨</option>
          <option value="false">정상</option>
        </select>
        <Button size="sm" type="submit">
          검색
        </Button>
      </form>

      {isLoading ? (
        <p className="font-bold text-gray-400 animate-pulse py-8">로딩 중...</p>
      ) : data && data.users.length > 0 ? (
        <>
          <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b-4 border-black dark:border-white bg-gray-100 dark:bg-gray-800">
                  <SortableHeader
                    field="nickname"
                    label="사용자"
                    align="left"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-4 py-3"
                  />
                  <th className="text-center px-3 py-3 font-black w-24">프로바이더</th>
                  <SortableHeader
                    field="role"
                    label="역할"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-3 w-24"
                  />
                  <SortableHeader
                    field="banned"
                    label="상태"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-3 w-20"
                  />
                  <SortableHeader
                    field="createdAt"
                    label="가입일"
                    currentSort={sort}
                    onSortChange={handleSortChange}
                    className="px-3 py-3 w-32"
                  />
                  <th className="text-center px-3 py-3 font-black w-20">액션</th>
                </tr>
              </thead>
              <tbody>
                {data.users.map((u) => (
                  <tr
                    key={u.publicId}
                    className="border-b-2 border-black/20 dark:border-white/20 hover:bg-yellow-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {u.profileUrl ? (
                          <img
                            src={resolveProfileUrl(u.profileUrl) ?? ""}
                            alt=""
                            className="w-7 h-7 shrink-0 border-2 border-black dark:border-white object-cover"
                          />
                        ) : (
                          <div className="w-7 h-7 shrink-0 border-2 border-black dark:border-white bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
                            <svg
                              width="14"
                              height="14"
                              viewBox="0 0 24 24"
                              fill="currentColor"
                              className="text-gray-400 dark:text-gray-500"
                            >
                              <circle cx="12" cy="9" r="3.5" />
                              <path d="M12 14c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z" />
                            </svg>
                          </div>
                        )}
                        <span className="font-bold">{u.nickname}</span>
                      </div>
                    </td>
                    <td className="text-center px-3 py-3 font-medium text-gray-600 dark:text-gray-400">{u.provider}</td>
                    <td className="text-center px-3 py-3">
                      <RoleBadge role={u.role} />
                    </td>
                    <td className="text-center px-3 py-3">{u.banned && <BannedBadge />}</td>
                    <td className="text-center px-3 py-3 tabular-nums text-gray-600 dark:text-gray-400 text-xs">
                      {new Date(u.createdAt).toLocaleDateString("ko-KR")}
                    </td>
                    <td className="text-center px-3 py-3">
                      <button
                        type="button"
                        onClick={() => setSelectedUserId(u.publicId)}
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
          <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <p className="text-gray-500 dark:text-gray-400 font-bold py-8 text-center">사용자가 없습니다.</p>
      )}

      {selectedUserId && <UserDetailDialog publicId={selectedUserId} onClose={() => setSelectedUserId(null)} />}
    </div>
  );
}
