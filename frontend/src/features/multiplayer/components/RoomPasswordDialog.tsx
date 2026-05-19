import { useState } from "react";
import { Dialog } from "@/shared/ui/Dialog";
import { Input } from "@/shared/ui/Input";
import { Button } from "@/shared/ui/Button";

interface RoomPasswordDialogProps {
  roomTitle: string;
  onClose: () => void;
  onSubmit: (password: string) => void;
}

export function RoomPasswordDialog({ roomTitle, onClose, onSubmit }: RoomPasswordDialogProps) {
  const [password, setPassword] = useState("");

  return (
    <Dialog
      onClose={onClose}
      title="비밀번호 입력"
      maxWidth="max-w-sm"
      footer={
        <>
          <Button variant="secondary" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button size="sm" onClick={() => onSubmit(password)} disabled={!password}>
            입장
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <p className="text-sm font-bold text-gray-600 dark:text-gray-400">
          <span className="text-black dark:text-white">{roomTitle}</span> 방에 입장하려면 비밀번호를 입력하세요.
        </p>
        <Input
          size="sm"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="비밀번호"
          maxLength={16}
          onKeyDown={(e) => {
            if (e.key === "Enter" && password) {
              onSubmit(password);
            }
          }}
        />
      </div>
    </Dialog>
  );
}
