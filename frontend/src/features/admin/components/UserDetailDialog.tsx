import { useEffect, useState } from "react";
import { Button } from "@/shared/ui/Button";
import { Input } from "@/shared/ui/Input";
import {
  useUserDetail,
  useUserHistory,
  useBanUser,
  useUnbanUser,
  useChangeRole,
  useForceChangeNickname,
  useForceDeleteProfileImage,
  useForceDeleteUser,
} from "@/features/admin/hooks/useAdminUsers";
import { useToastStore } from "@/shared/stores/useToastStore";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { ApiError } from "@/shared/api/client";
import { resolveProfileUrl } from "@/shared/utils/url";
import { getSimilarityColor } from "@/shared/utils/similarity";

function canModify(actorRole: string | undefined, targetRole: string): boolean {
  if (targetRole === "SUPERADMIN") {
    return false;
  }
  if (targetRole === "USER") {
    return true;
  }
  if (targetRole === "ADMIN" && actorRole === "SUPERADMIN") {
    return true;
  }
  return false;
}

function canChangeRole(actorRole: string | undefined, targetRole: string): boolean {
  if (actorRole !== "SUPERADMIN") {
    return false;
  }
  return targetRole !== "SUPERADMIN";
}

function roleToggle(targetRole: string): { label: string; nextRole: "USER" | "ADMIN" } {
  if (targetRole === "ADMIN") {
    return { label: "일반으로 강등", nextRole: "USER" };
  }
  return { label: "관리자로 승격", nextRole: "ADMIN" };
}

function roleLabel(role: string): string {
  if (role === "SUPERADMIN") {
    return "최고관리자";
  }
  if (role === "ADMIN") {
    return "관리자";
  }
  return "일반";
}

interface UserDetailDialogProps {
  publicId: string;
  onClose: () => void;
}

export function UserDetailDialog({ publicId, onClose }: UserDetailDialogProps) {
  const { data: user, isLoading } = useUserDetail(publicId);
  const { data: history } = useUserHistory(publicId);
  const [newNickname, setNewNickname] = useState("");
  const [showHistory, setShowHistory] = useState(false);

  const banMutation = useBanUser();
  const unbanMutation = useUnbanUser();
  const roleMutation = useChangeRole();
  const nicknameMutation = useForceChangeNickname();
  const profileMutation = useForceDeleteProfileImage();
  const deleteMutation = useForceDeleteUser();
  const { addToast } = useToastStore();
  const actorRole = useAuthStore((s) => s.user?.role);

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

  function handleAction(action: () => void, confirmMsg: string) {
    if (!window.confirm(confirmMsg)) {
      return;
    }
    action();
  }

  function onError(err: Error) {
    addToast(err instanceof ApiError ? err.message : "작업 실패", "error");
  }

  if (isLoading || !user) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
        <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal p-8">
          <p className="font-bold text-gray-400 animate-pulse">로딩 중...</p>
        </div>
      </div>
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 overflow-y-auto py-8"
      role="dialog"
      aria-modal="true"
    >
      <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-lg my-auto">
        <div className="px-6 py-5 border-b-4 border-black dark:border-white flex items-center justify-between">
          <h2 className="text-xl font-black">사용자 상세</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="text-2xl font-black leading-none hover:text-red-500"
          >
            &times;
          </button>
        </div>

        <div className="px-6 py-5 space-y-4">
          {/* 프로필 */}
          <div className="flex items-center gap-4">
            {user.profileUrl ? (
              <img
                src={resolveProfileUrl(user.profileUrl) ?? ""}
                alt=""
                className="w-16 h-16 shrink-0 border-4 border-black dark:border-white object-cover"
              />
            ) : (
              <div className="w-16 h-16 shrink-0 border-4 border-black dark:border-white bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
                <svg
                  width="32"
                  height="32"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  className="text-gray-400 dark:text-gray-500"
                >
                  <circle cx="12" cy="9" r="3.5" />
                  <path d="M12 14c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z" />
                </svg>
              </div>
            )}
            <div>
              <p className="text-lg font-black">{user.nickname}</p>
              <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">
                {user.provider} / {roleLabel(user.role)} / {new Date(user.createdAt).toLocaleDateString("ko-KR")}
              </p>
              {user.email && (
                <p className="text-xs text-gray-500 dark:text-gray-400 font-medium mt-0.5">{user.email}</p>
              )}
              {user.banned && (
                <p className="text-xs font-black text-red-500 mt-0.5">
                  차단됨 ({user.bannedAt ? new Date(user.bannedAt).toLocaleString("ko-KR") : ""})
                </p>
              )}
            </div>
          </div>

          {/* 활동 요약 */}
          <div className="grid grid-cols-4 gap-2">
            {[
              { label: "참여", value: user.activity.totalParticipations },
              { label: "클리어", value: user.activity.totalClears },
              { label: "평균시도", value: user.activity.avgAttemptCount.toFixed(1) },
              { label: "최고순위", value: user.activity.bestRank ?? "-" },
            ].map((item) => (
              <div key={item.label} className="border-2 border-black dark:border-white p-2 text-center">
                <p className="text-[10px] text-gray-500 dark:text-gray-400 font-bold">{item.label}</p>
                <p className="font-black tabular-nums">{item.value}</p>
              </div>
            ))}
          </div>

          {/* 액션 */}
          <div className="border-t-2 border-black/20 dark:border-white/20 pt-4 space-y-3">
            {!canModify(actorRole, user.role) && (
              <p className="text-xs font-bold text-amber-600 dark:text-amber-400">
                이 사용자에 대한 변경 권한이 부족합니다.
              </p>
            )}
            {/* 역할 변경 */}
            <div className="flex flex-col gap-1">
              <div className="flex gap-2 items-center">
                <span className="text-sm font-bold w-20">역할:</span>
                {user.role === "SUPERADMIN" ? (
                  <Button size="sm" variant="secondary" disabled>
                    변경 불가
                  </Button>
                ) : (
                  (() => {
                    const toggle = roleToggle(user.role);
                    return (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() =>
                          handleAction(
                            () =>
                              roleMutation.mutate(
                                { publicId, role: toggle.nextRole },
                                { onSuccess: () => addToast("역할이 변경되었습니다.", "success"), onError },
                              ),
                            `${toggle.label}하시겠습니까?`,
                          )
                        }
                        disabled={!canChangeRole(actorRole, user.role)}
                        isLoading={roleMutation.isPending}
                      >
                        {toggle.label}
                      </Button>
                    );
                  })()
                )}
              </div>
              {user.role !== "SUPERADMIN" && actorRole !== "SUPERADMIN" && (
                <p className="text-xs font-bold text-amber-600 dark:text-amber-400 pl-[5.5rem]">
                  SUPERADMIN만 역할을 변경할 수 있습니다.
                </p>
              )}
            </div>

            {/* 차단/해제 */}
            <div className="flex gap-2 items-center">
              <span className="text-sm font-bold w-20">차단:</span>
              {user.banned ? (
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() =>
                    handleAction(
                      () =>
                        unbanMutation.mutate(publicId, {
                          onSuccess: () => addToast("차단이 해제되었습니다.", "success"),
                          onError,
                        }),
                      "차단을 해제하시겠습니까?",
                    )
                  }
                  disabled={!canModify(actorRole, user.role)}
                  isLoading={unbanMutation.isPending}
                >
                  차단 해제
                </Button>
              ) : (
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() =>
                    handleAction(
                      () =>
                        banMutation.mutate(publicId, {
                          onSuccess: () => addToast("차단되었습니다.", "success"),
                          onError,
                        }),
                      "이 사용자를 차단하시겠습니까?",
                    )
                  }
                  disabled={!canModify(actorRole, user.role)}
                  isLoading={banMutation.isPending}
                >
                  차단
                </Button>
              )}
            </div>

            {/* 닉네임 변경 */}
            <div className="flex gap-2 items-center">
              <span className="text-sm font-bold w-20">닉네임:</span>
              <Input
                value={newNickname}
                onChange={(e) => setNewNickname(e.target.value)}
                placeholder="새 닉네임"
                className="!py-1.5 !text-sm flex-1"
              />
              <Button
                size="sm"
                onClick={() =>
                  handleAction(
                    () =>
                      nicknameMutation.mutate(
                        { publicId, nickname: newNickname.trim() },
                        {
                          onSuccess: () => {
                            addToast("닉네임이 변경되었습니다.", "success");
                            setNewNickname("");
                          },
                          onError,
                        },
                      ),
                    `닉네임을 "${newNickname.trim()}"(으)로 변경하시겠습니까?`,
                  )
                }
                disabled={!newNickname.trim() || !canModify(actorRole, user.role)}
                isLoading={nicknameMutation.isPending}
              >
                변경
              </Button>
            </div>

            {/* 프로필 이미지 삭제 */}
            {user.profileUrl && (
              <div className="flex gap-2 items-center">
                <span className="text-sm font-bold w-20">프로필:</span>
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() =>
                    handleAction(
                      () =>
                        profileMutation.mutate(publicId, {
                          onSuccess: () => addToast("프로필 이미지가 삭제되었습니다.", "success"),
                          onError,
                        }),
                      "프로필 이미지를 삭제하시겠습니까?",
                    )
                  }
                  disabled={!canModify(actorRole, user.role)}
                  isLoading={profileMutation.isPending}
                >
                  이미지 삭제
                </Button>
              </div>
            )}

            {/* 강제 탈퇴 (SUPERADMIN 전용) */}
            <div className="flex flex-col gap-1">
              <div className="flex gap-2 items-center">
                <span className="text-sm font-bold w-20">탈퇴:</span>
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() =>
                    handleAction(
                      () =>
                        deleteMutation.mutate(publicId, {
                          onSuccess: () => {
                            addToast("강제 탈퇴되었습니다.", "success");
                            onClose();
                          },
                          onError,
                        }),
                      "이 사용자를 강제 탈퇴시키겠습니까? 되돌릴 수 없습니다.",
                    )
                  }
                  disabled={actorRole !== "SUPERADMIN" || user.role === "SUPERADMIN"}
                  isLoading={deleteMutation.isPending}
                >
                  강제 탈퇴
                </Button>
              </div>
              {actorRole !== "SUPERADMIN" && (
                <p className="text-xs font-bold text-amber-600 dark:text-amber-400 pl-[5.5rem]">
                  SUPERADMIN만 강제 탈퇴를 수행할 수 있습니다.
                </p>
              )}
            </div>
          </div>

          {/* 게임 이력 */}
          <div className="border-t-2 border-black/20 dark:border-white/20 pt-4">
            <button
              type="button"
              onClick={() => setShowHistory(!showHistory)}
              className="text-sm font-black text-indigo-600 dark:text-indigo-400 hover:underline"
            >
              {showHistory ? "게임 이력 숨기기" : "게임 이력 보기"}
            </button>

            {showHistory && history && (
              <div className="mt-3 max-h-60 overflow-y-auto border-2 border-black dark:border-white">
                {history.history.length === 0 ? (
                  <p className="p-3 text-sm text-gray-500 dark:text-gray-400 font-medium">이력이 없습니다.</p>
                ) : (
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b-2 border-black dark:border-white bg-gray-100 dark:bg-gray-800">
                        <th className="px-2 py-1.5 font-black text-left">날짜</th>
                        <th className="px-2 py-1.5 font-black text-left">문장</th>
                        <th className="px-2 py-1.5 font-black text-center">상태</th>
                        <th className="px-2 py-1.5 font-black text-center">유사도</th>
                        <th className="px-2 py-1.5 font-black text-center">시도</th>
                        <th className="px-2 py-1.5 font-black text-center">순위</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.history.map((h) => {
                        const color = getSimilarityColor(Number(h.bestSimilarity));
                        return (
                          <tr key={h.date} className="border-b border-black/10 dark:border-white/10">
                            <td className="px-2 py-1.5 tabular-nums">{h.date}</td>
                            <td className="px-2 py-1.5 truncate max-w-[120px]">{h.sentence}</td>
                            <td className="px-2 py-1.5 text-center font-bold">{h.gameStatus}</td>
                            <td className="px-2 py-1.5 text-center font-black tabular-nums" style={{ color: color.bg }}>
                              {Number(h.bestSimilarity).toFixed(1)}%
                            </td>
                            <td className="px-2 py-1.5 text-center tabular-nums">{h.attemptCount}</td>
                            <td className="px-2 py-1.5 text-center tabular-nums">{h.finalRank ?? "-"}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
