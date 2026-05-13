import { useState, useEffect, useRef } from "react";
import { ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";
import { Input } from "@/shared/ui/Input";
import { register } from "@/features/auth/api";

const USERNAME_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;
const USERNAME_MIN = 4;
const USERNAME_MAX = 20;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 72;

interface RegisterDialogProps {
  onClose: () => void;
  onSuccess: () => void;
}

function validateUsername(value: string): string | null {
  if (value.length < USERNAME_MIN) {
    return `${USERNAME_MIN}자 이상 입력해주세요`;
  }
  if (value.length > USERNAME_MAX) {
    return `${USERNAME_MAX}자 이하로 입력해주세요`;
  }
  if (!USERNAME_PATTERN.test(value)) {
    return "영문으로 시작하며, 영문/숫자/밑줄만 사용 가능합니다";
  }
  return null;
}

function validatePassword(value: string): string | null {
  if (value.length < PASSWORD_MIN) {
    return `${PASSWORD_MIN}자 이상 입력해주세요`;
  }
  return null;
}

export function RegisterDialog({ onClose, onSuccess }: RegisterDialogProps) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const usernameError = username.length > 0 ? validateUsername(username) : null;
  const passwordError = password.length > 0 ? validatePassword(password) : null;
  const confirmError =
    passwordConfirm.length > 0 && password !== passwordConfirm ? "비밀번호가 일치하지 않습니다" : null;
  const canSubmit =
    !isSubmitting &&
    username.length >= USERNAME_MIN &&
    password.length >= PASSWORD_MIN &&
    password === passwordConfirm &&
    !usernameError &&
    !passwordError;

  const handleSubmit = async () => {
    const uError = validateUsername(username);
    if (uError) {
      setError(uError);
      return;
    }

    const pError = validatePassword(password);
    if (pError) {
      setError(pError);
      return;
    }

    if (password !== passwordConfirm) {
      setError("비밀번호가 일치하지 않습니다");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await register(username, password);
      onSuccess();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("회원가입에 실패했습니다");
      }
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog
      onClose={onClose}
      title="회원가입"
      disableClose={isSubmitting}
      footer={
        <>
          <Button
            variant="secondary"
            size="sm"
            className="flex-1 min-h-[44px]"
            onClick={onClose}
            disabled={isSubmitting}
          >
            취소
          </Button>
          <Button
            size="sm"
            className="flex-1 min-h-[44px]"
            onClick={handleSubmit}
            isLoading={isSubmitting}
            disabled={!canSubmit}
          >
            가입
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <ul className="text-xs text-gray-500 dark:text-gray-400 space-y-0.5">
          <li>
            - 아이디: {USERNAME_MIN}~{USERNAME_MAX}자, 영문 시작, 영문/숫자/밑줄
          </li>
          <li>- 비밀번호: {PASSWORD_MIN}자 이상</li>
          <li>- 닉네임은 자동 생성되며, 마이페이지에서 변경 가능</li>
        </ul>

        <div className="space-y-3">
          <div className="space-y-1">
            <Input
              ref={inputRef}
              value={username}
              onChange={(e) => {
                if (e.target.value.length <= USERNAME_MAX) {
                  setUsername(e.target.value);
                  setError(null);
                }
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) {
                  handleSubmit();
                }
              }}
              placeholder="아이디"
              maxLength={USERNAME_MAX}
              disabled={isSubmitting}
              autoComplete="username"
            />
            {usernameError && <p className="text-sm font-medium text-red-500">{usernameError}</p>}
          </div>

          <div className="space-y-1">
            <Input
              type="password"
              value={password}
              onChange={(e) => {
                if (e.target.value.length <= PASSWORD_MAX) {
                  setPassword(e.target.value);
                  setError(null);
                }
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) {
                  handleSubmit();
                }
              }}
              placeholder="비밀번호"
              maxLength={PASSWORD_MAX}
              disabled={isSubmitting}
              autoComplete="new-password"
            />
            {passwordError && <p className="text-sm font-medium text-red-500">{passwordError}</p>}
          </div>

          <div className="space-y-1">
            <Input
              type="password"
              value={passwordConfirm}
              onChange={(e) => {
                setPasswordConfirm(e.target.value);
                setError(null);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) {
                  handleSubmit();
                }
              }}
              placeholder="비밀번호 확인"
              disabled={isSubmitting}
              autoComplete="new-password"
            />
            {confirmError && <p className="text-sm font-medium text-red-500">{confirmError}</p>}
          </div>
        </div>

        {error && <p className="text-sm font-medium text-red-500">{error}</p>}
      </div>
    </Dialog>
  );
}
