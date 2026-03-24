import { useState } from "react";
import type { MeResponse } from "@/shared/api/types";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { useToastStore } from "@/shared/stores/useToastStore";
import { Card } from "@/shared/ui/Card";
import { Button } from "@/shared/ui/Button";
import { logout, deleteAccount } from "@/features/auth/api";
import { ConfirmDialog } from "@/features/auth/components/ConfirmDialog";
import { NicknameEditDialog } from "@/features/auth/components/NicknameEditDialog";

export function MyPage() {
  const { user, clearUser, updateUser } = useAuthStore();
  const addToast = useToastStore((s) => s.addToast);

  const [isNicknameEditOpen, setIsNicknameEditOpen] = useState(false);
  const [isLogoutConfirmOpen, setIsLogoutConfirmOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const handleNicknameSuccess = (updatedUser: MeResponse) => {
    updateUser({ nickname: updatedUser.nickname });
    addToast("닉네임이 변경되었습니다", "success");
    setIsNicknameEditOpen(false);
  };

  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logout();
      addToast("로그아웃되었습니다", "success");
    } catch {
      addToast("로그아웃 중 문제가 발생했습니다", "error");
    } finally {
      clearUser();
      setIsLoggingOut(false);
      setIsLogoutConfirmOpen(false);
    }
  };

  const handleDeleteAccount = async () => {
    setIsDeleting(true);
    try {
      await deleteAccount();
      clearUser();
      addToast("회원 탈퇴가 완료되었습니다", "success");
      setIsDeleteConfirmOpen(false);
    } catch {
      addToast("회원 탈퇴에 실패했습니다. 다시 시도해주세요.", "error");
      setIsDeleting(false);
    }
  };

  return (
    <div className="max-w-md mx-auto space-y-6">
      <h1 className="text-4xl font-black">마이페이지</h1>

      <Card className="flex flex-col items-center space-y-4 py-8">
        {user?.profileUrl ? (
          <img
            src={user.profileUrl}
            alt="프로필"
            className="w-24 h-24 border-4 border-black dark:border-white object-cover"
          />
        ) : (
          <div className="w-24 h-24 border-4 border-black dark:border-white bg-gray-200 dark:bg-gray-800 flex items-center justify-center">
            <svg
              width="48"
              height="48"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="text-gray-400 dark:text-gray-500"
            >
              <circle cx="12" cy="9" r="3.5" />
              <path d="M12 14c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z" />
            </svg>
          </div>
        )}

        <div className="text-center">
          <p className="text-sm font-extrabold uppercase tracking-wider text-gray-600 dark:text-gray-400 mb-1">
            닉네임
          </p>
          <div className="flex items-center justify-center gap-2">
            <p className="text-2xl font-black">{user?.nickname}</p>
            <Button variant="secondary" size="sm" onClick={() => setIsNicknameEditOpen(true)}>
              변경
            </Button>
          </div>
        </div>
      </Card>

      <div className="space-y-3">
        <Button
          variant="secondary"
          className="w-full"
          onClick={() => setIsLogoutConfirmOpen(true)}
          disabled={isLoggingOut}
        >
          로그아웃
        </Button>

        <Button variant="danger" className="w-full" onClick={() => setIsDeleteConfirmOpen(true)} disabled={isDeleting}>
          회원 탈퇴
        </Button>
      </div>

      {isNicknameEditOpen && (
        <NicknameEditDialog
          onClose={() => setIsNicknameEditOpen(false)}
          currentNickname={user?.nickname ?? ""}
          onSuccess={handleNicknameSuccess}
        />
      )}

      <ConfirmDialog
        isOpen={isLogoutConfirmOpen}
        onClose={() => setIsLogoutConfirmOpen(false)}
        onConfirm={handleLogout}
        title="로그아웃"
        message="로그아웃하시겠습니까?"
        confirmLabel="로그아웃"
        isLoading={isLoggingOut}
      />

      <ConfirmDialog
        isOpen={isDeleteConfirmOpen}
        onClose={() => setIsDeleteConfirmOpen(false)}
        onConfirm={handleDeleteAccount}
        title="회원 탈퇴"
        message="탈퇴 시 게임 기록이 익명 처리되며 복구할 수 없습니다. 정말 탈퇴하시겠습니까?"
        confirmLabel="탈퇴하기"
        isLoading={isDeleting}
      />
    </div>
  );
}
