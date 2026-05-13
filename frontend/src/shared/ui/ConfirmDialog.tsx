import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";

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
  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      disableClose={isLoading}
      footer={
        <>
          <Button variant="secondary" size="sm" className="flex-1 min-h-[44px]" onClick={onClose} disabled={isLoading}>
            취소
          </Button>
          <Button variant="danger" size="sm" className="flex-1 min-h-[44px]" onClick={onConfirm} isLoading={isLoading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-sm text-gray-600 dark:text-gray-400 font-medium whitespace-pre-line">{message}</p>
    </Dialog>
  );
}
