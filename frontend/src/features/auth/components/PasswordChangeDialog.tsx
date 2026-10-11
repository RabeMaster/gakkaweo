import { useId, useState } from "react";
import { ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";
import { Input } from "@/shared/ui/Input";
import { changePassword } from "@/features/auth/api";
import { validatePasswordChange } from "@/features/auth/utils/password";

interface PasswordChangeDialogProps {
  onClose: () => void;
  onSuccess: () => void;
}

export function PasswordChangeDialog({ onClose, onSuccess }: PasswordChangeDialogProps) {
  const formId = useId();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validationError = validatePasswordChange(currentPassword, newPassword, confirmation);

  const handleSubmit = async () => {
    if (isSubmitting) {
      return;
    }

    if (validationError) {
      setError(validationError);
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await changePassword(currentPassword, newPassword);
      onSuccess();
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "비밀번호를 변경하지 못했습니다. 다시 시도해 주세요.");
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog
      onClose={onClose}
      title="비밀번호 변경"
      disableClose={isSubmitting}
      maxWidth="max-w-md"
      footer={
        <>
          <Button
            size="sm"
            variant="secondary"
            className="flex-1 min-h-[44px]"
            onClick={onClose}
            disabled={isSubmitting}
          >
            취소
          </Button>
          <Button
            size="sm"
            type="submit"
            form={formId}
            className="flex-1 min-h-[44px]"
            isLoading={isSubmitting}
            disabled={Boolean(validationError)}
          >
            변경하고 로그아웃
          </Button>
        </>
      }
    >
      <form
        id={formId}
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void handleSubmit();
        }}
      >
        <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
          새 비밀번호는 8자 이상 입력해 주세요. 변경하면 모든 기기에서 로그아웃됩니다.
        </p>
        <label className="block text-sm font-bold space-y-1">
          <span>현재 비밀번호</span>
          <Input
            type="password"
            autoComplete="current-password"
            autoFocus
            value={currentPassword}
            onChange={(event) => {
              setCurrentPassword(event.target.value);
              setError(null);
            }}
            disabled={isSubmitting}
            required
            maxLength={72}
          />
        </label>
        <label className="block text-sm font-bold space-y-1">
          <span>새 비밀번호</span>
          <Input
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => {
              setNewPassword(event.target.value);
              setError(null);
            }}
            disabled={isSubmitting}
            required
            minLength={8}
            maxLength={72}
          />
        </label>
        <label className="block text-sm font-bold space-y-1">
          <span>새 비밀번호 확인</span>
          <Input
            type="password"
            autoComplete="new-password"
            value={confirmation}
            onChange={(event) => {
              setConfirmation(event.target.value);
              setError(null);
            }}
            disabled={isSubmitting}
            required
            minLength={8}
            maxLength={72}
          />
        </label>
        {newPassword && validationError && !error && (
          <p className="text-sm font-medium text-red-600 dark:text-red-400">{validationError}</p>
        )}
        {error && (
          <p role="alert" className="text-sm font-medium text-red-600 dark:text-red-400">
            {error}
          </p>
        )}
      </form>
    </Dialog>
  );
}
