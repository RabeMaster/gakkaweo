import { useState, useRef, useEffect } from "react";
import type { ChangeEvent } from "react";
import type { MeResponse } from "@/shared/api/types";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { useToastStore } from "@/shared/stores/useToastStore";
import { Card } from "@/shared/ui/Card";
import { Button } from "@/shared/ui/Button";
import { logout, deleteAccount, deleteProfileImage } from "@/features/auth/api";
import { resolveProfileUrl } from "@/shared/utils/url";
import { ConfirmDialog } from "@/features/auth/components/ConfirmDialog";
import { NicknameEditDialog } from "@/features/auth/components/NicknameEditDialog";
import { ProfileImageCropDialog } from "@/features/auth/components/ProfileImageCropDialog";
import { ProfileImagePopover } from "@/features/auth/components/ProfileImagePopover";

export function MyPage() {
  const { user, clearUser, updateUser } = useAuthStore();
  const addToast = useToastStore((s) => s.addToast);

  const [isNicknameEditOpen, setIsNicknameEditOpen] = useState(false);
  const [isLogoutConfirmOpen, setIsLogoutConfirmOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const [isPopoverOpen, setIsPopoverOpen] = useState(false);
  const [selectedImageSrc, setSelectedImageSrc] = useState<string | null>(null);
  const [isDeleteImageConfirmOpen, setIsDeleteImageConfirmOpen] = useState(false);
  const [isDeletingImage, setIsDeletingImage] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const profileBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(
    () => () => {
      if (selectedImageSrc) {
        URL.revokeObjectURL(selectedImageSrc);
      }
    },
    [selectedImageSrc],
  );

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

  const handleFileSelect = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) {
      return;
    }

    if (selectedImageSrc) {
      URL.revokeObjectURL(selectedImageSrc);
    }

    const blobUrl = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
      const ctx = canvas.getContext("2d");
      URL.revokeObjectURL(blobUrl);
      if (!ctx) {
        addToast("이미지 편집을 지원하지 않는 환경입니다", "error");
        return;
      }
      ctx.drawImage(img, 0, 0);
      setSelectedImageSrc(canvas.toDataURL("image/png"));
    };
    img.onerror = () => {
      URL.revokeObjectURL(blobUrl);
      addToast("이미지를 불러올 수 없습니다", "error");
    };
    img.src = blobUrl;

    setIsPopoverOpen(false);
    e.target.value = "";
  };

  const handleCropSuccess = (response: MeResponse) => {
    updateUser({ profileUrl: response.profileUrl });
    addToast("프로필 이미지가 변경되었습니다", "success");

    if (selectedImageSrc) {
      URL.revokeObjectURL(selectedImageSrc);
    }
    setSelectedImageSrc(null);
  };

  const handleCropClose = () => {
    if (selectedImageSrc) {
      URL.revokeObjectURL(selectedImageSrc);
    }
    setSelectedImageSrc(null);
  };

  const handleDeleteImage = async () => {
    setIsDeletingImage(true);
    try {
      const response = await deleteProfileImage();
      updateUser({ profileUrl: response.profileUrl });
      addToast("프로필 이미지가 삭제되었습니다", "success");
      setIsDeleteImageConfirmOpen(false);
    } catch {
      addToast("프로필 이미지 삭제에 실패했습니다", "error");
    } finally {
      setIsDeletingImage(false);
    }
  };

  return (
    <div className="max-w-md mx-auto space-y-6">
      <h1 className="text-2xl md:text-4xl font-black">마이페이지</h1>

      <Card className="flex flex-col items-center space-y-4 py-8">
        <div className="relative">
          <button
            ref={profileBtnRef}
            type="button"
            onClick={() => setIsPopoverOpen((prev) => !prev)}
            className="group relative cursor-pointer"
            aria-label="프로필 이미지 변경"
          >
            {user?.profileUrl ? (
              <img
                src={resolveProfileUrl(user.profileUrl) ?? ""}
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
            <div className="absolute inset-0 border-4 border-black dark:border-white bg-black/40 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity flex items-center justify-center">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="white">
                <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" />
              </svg>
            </div>
          </button>

          {isPopoverOpen && (
            <ProfileImagePopover
              hasImage={!!user?.profileUrl}
              triggerRef={profileBtnRef}
              onChangeClick={() => {
                setIsPopoverOpen(false);
                fileInputRef.current?.click();
              }}
              onDeleteClick={() => {
                setIsPopoverOpen(false);
                setIsDeleteImageConfirmOpen(true);
              }}
              onClose={() => setIsPopoverOpen(false)}
            />
          )}
        </div>

        <input ref={fileInputRef} type="file" accept="image/*" onChange={handleFileSelect} className="hidden" />

        <div className="text-center">
          <p className="text-sm font-extrabold uppercase tracking-wider text-gray-600 dark:text-gray-400 mb-1">
            닉네임
          </p>
          <p className="text-2xl font-black">
            <span className="relative">
              {user?.nickname}
              <button
                type="button"
                onClick={() => setIsNicknameEditOpen(true)}
                className="absolute left-full top-1/2 -translate-y-1/2 ml-1.5 text-base leading-none cursor-pointer hover:scale-110 transition-transform"
                aria-label="닉네임 변경"
              >
                ✏️
              </button>
            </span>
          </p>
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

      {selectedImageSrc && (
        <ProfileImageCropDialog imageSrc={selectedImageSrc} onClose={handleCropClose} onSuccess={handleCropSuccess} />
      )}

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

      <ConfirmDialog
        isOpen={isDeleteImageConfirmOpen}
        onClose={() => setIsDeleteImageConfirmOpen(false)}
        onConfirm={handleDeleteImage}
        title="프로필 이미지 삭제"
        message="프로필 이미지를 삭제하시겠습니까?"
        confirmLabel="삭제"
        isLoading={isDeletingImage}
      />
    </div>
  );
}
