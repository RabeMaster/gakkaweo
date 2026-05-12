import { useRef, useState } from "react";
import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";
import { useUploadCsv } from "@/features/admin/hooks/useAdminSentences";
import { useToastStore } from "@/shared/stores/useToastStore";
import { ApiError } from "@/shared/api/client";

interface CsvUploadDialogProps {
  onClose: () => void;
}

export function CsvUploadDialog({ onClose }: CsvUploadDialogProps) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const uploadMutation = useUploadCsv();
  const { addToast } = useToastStore();

  function handleUpload() {
    if (!file) {
      return;
    }
    uploadMutation.mutate(file, {
      onSuccess: (res) => {
        addToast(`업로드 완료: 성공 ${res.successCount}건, 중복 ${res.duplicateCount}건`, "success");
        onClose();
      },
      onError: (err) => addToast(err instanceof ApiError ? err.message : "업로드 실패", "error"),
    });
  }

  return (
    <Dialog
      onClose={onClose}
      title="CSV 업로드"
      disableClose={uploadMutation.isPending}
      footer={
        <>
          <Button variant="secondary" size="sm" className="flex-1" onClick={onClose}>
            취소
          </Button>
          <Button
            size="sm"
            className="flex-1"
            onClick={handleUpload}
            disabled={!file}
            isLoading={uploadMutation.isPending}
          >
            업로드
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="text-sm text-gray-600 dark:text-gray-400 font-medium">
          한 줄에 문장 하나씩 작성된 CSV 파일을 업로드하세요.
        </p>

        <input
          ref={fileRef}
          type="file"
          accept=".csv"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="block w-full text-sm font-bold dark:[color-scheme:dark] file:mr-3 file:border-4 file:border-black file:dark:border-white file:shadow-brutal-sm file:bg-yellow-300 file:text-black file:font-bold file:px-3 file:py-1.5 file:text-sm file:cursor-pointer"
        />

        {file && <p className="text-sm font-bold">{file.name}</p>}
      </div>
    </Dialog>
  );
}
