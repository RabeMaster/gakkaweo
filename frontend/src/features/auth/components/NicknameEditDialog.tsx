import { useState, useEffect, useRef } from "react";
import type { MeResponse } from "@/shared/api/types";
import { ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";
import { Input } from "@/shared/ui/Input";
import { changeNickname } from "@/features/auth/api";

const NICKNAME_PATTERN = /^[가-힣a-zA-Z0-9_ ]+$/;
const MIN_LENGTH = 2;
const MAX_LENGTH = 12;
const FORBIDDEN_WORDS = [
  "admin",
  "administrator",
  "manager",
  "moderator",
  "operator",
  "staff",
  "system",
  "official",
  "developer",
  "관리자",
  "관리인",
  "관리팀",
  "운영자",
  "운영진",
  "운영인",
  "운영팀",
  "어드민",
  "매니저",
  "스태프",
  "스탭",
  "개발자",
  "개발진",
  "개발팀",
  "공식",
  "시스템",
];

interface NicknameEditDialogProps {
  onClose: () => void;
  currentNickname: string;
  onSuccess: (updatedUser: MeResponse) => void;
}

function normalize(value: string): string {
  return value.trim().replace(/\s+/g, " ");
}

function containsForbiddenWord(value: string): boolean {
  const stripped = value.toLowerCase().replace(/[\s_]+/g, "");
  return FORBIDDEN_WORDS.some((word) => stripped.includes(word));
}

function validate(value: string): string | null {
  const normalized = normalize(value);
  if (normalized.length < MIN_LENGTH) {
    return `${MIN_LENGTH}자 이상 입력해주세요`;
  }
  if (normalized.length > MAX_LENGTH) {
    return `${MAX_LENGTH}자 이하로 입력해주세요`;
  }
  if (!NICKNAME_PATTERN.test(normalized)) {
    return "한글, 영문, 숫자, 밑줄, 공백만 사용 가능합니다";
  }
  if (containsForbiddenWord(normalized)) {
    return "사용할 수 없는 닉네임입니다";
  }
  return null;
}

export function NicknameEditDialog({ onClose, currentNickname, onSuccess }: NicknameEditDialogProps) {
  const [nickname, setNickname] = useState(currentNickname);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const handleChange = (value: string) => {
    if (value.length <= MAX_LENGTH) {
      setNickname(value);
      setError(null);
    }
  };

  const normalized = normalize(nickname);
  const isUnchanged = normalized === currentNickname;
  const clientError = normalized.length > 0 ? validate(nickname) : null;
  const canSubmit = !isSubmitting && !isUnchanged && !clientError && normalized.length >= MIN_LENGTH;

  const handleSubmit = async () => {
    const validationError = validate(nickname);
    if (validationError) {
      setError(validationError);
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const updatedUser = await changeNickname(normalized);
      onSuccess(updatedUser);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("닉네임 변경에 실패했습니다");
      }
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog
      onClose={onClose}
      title="닉네임 변경"
      disableClose={isSubmitting}
      footer={
        <>
          <Button variant="secondary" size="sm" className="flex-1" onClick={onClose} disabled={isSubmitting}>
            취소
          </Button>
          <Button size="sm" className="flex-1" onClick={handleSubmit} isLoading={isSubmitting} disabled={!canSubmit}>
            변경
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <ul className="text-xs text-gray-500 dark:text-gray-400 space-y-0.5">
          <li>- 한글, 영문, 숫자, 밑줄(_), 공백 사용 가능</li>
          <li>
            - {MIN_LENGTH}~{MAX_LENGTH}자, 연속 공백은 하나로 처리
          </li>
          <li>- 관리자 사칭 닉네임 사용 불가</li>
        </ul>

        <div className="space-y-2">
          <Input
            ref={inputRef}
            value={nickname}
            onChange={(e) => handleChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && canSubmit) {
                handleSubmit();
              }
            }}
            placeholder="새 닉네임"
            maxLength={MAX_LENGTH}
            disabled={isSubmitting}
          />

          <div className="flex items-center justify-between min-h-[1.25rem]">
            {error ? <p className="text-sm font-medium text-red-500">{error}</p> : <span />}
            <p className="text-sm text-gray-500 tabular-nums">
              {normalized.length}/{MAX_LENGTH}
            </p>
          </div>
        </div>
      </div>
    </Dialog>
  );
}
