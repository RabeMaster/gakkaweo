import { useEffect, useRef } from "react";
import { Button } from "@/shared/ui/Button";

interface ConfirmDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel: string;
  isLoading?: boolean;
}

export function ConfirmDialog({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel,
  isLoading,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const handleClickOutside = (e: MouseEvent) => {
      if (dialogRef.current && !dialogRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div
        ref={dialogRef}
        className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-sm"
      >
        <div className="px-6 py-5 space-y-3">
          <h2 className="text-xl font-black">{title}</h2>
          <p className="text-sm text-gray-600 dark:text-gray-400 font-medium">{message}</p>
        </div>

        <div className="flex gap-3 px-6 py-4 border-t-4 border-black dark:border-white">
          <Button variant="secondary" size="sm" className="flex-1" onClick={onClose} disabled={isLoading}>
            취소
          </Button>
          <Button variant="danger" size="sm" className="flex-1" onClick={onConfirm} isLoading={isLoading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
