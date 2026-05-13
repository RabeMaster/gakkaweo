import { Button } from "@/shared/ui/Button";
import { Dialog } from "@/shared/ui/Dialog";
import { getAuditActionLabel, getAuditTargetTypeLabel } from "@/features/admin/labels";
import type { AuditLog } from "@/features/admin/types";

interface AuditLogDetailDialogProps {
  log: AuditLog;
  onClose: () => void;
}

export function AuditLogDetailDialog({ log, onClose }: AuditLogDetailDialogProps) {
  return (
    <Dialog
      onClose={onClose}
      title="감사 로그 상세"
      maxWidth="max-w-md"
      footer={
        <Button variant="secondary" size="sm" className="w-full" onClick={onClose}>
          닫기
        </Button>
      }
    >
      <div className="space-y-3 text-sm">
        <div>
          <p className="text-xs font-bold text-gray-500 dark:text-gray-400">시간</p>
          <p className="font-bold tabular-nums">{new Date(log.createdAt).toLocaleString("ko-KR")}</p>
        </div>
        <div>
          <p className="text-xs font-bold text-gray-500 dark:text-gray-400">관리자</p>
          <p className="font-bold">{log.adminNickname}</p>
        </div>
        <div>
          <p className="text-xs font-bold text-gray-500 dark:text-gray-400">액션</p>
          <p>
            <span
              className="inline-block px-1.5 py-0.5 bg-gray-200 dark:bg-gray-700 font-black text-xs"
              title={log.action}
            >
              {getAuditActionLabel(log.action)}
            </span>
          </p>
        </div>
        <div>
          <p className="text-xs font-bold text-gray-500 dark:text-gray-400">대상</p>
          <p className="font-medium break-all" title={log.targetType}>
            {getAuditTargetTypeLabel(log.targetType)}
            {log.targetId ? `: ${log.targetId}` : ""}
          </p>
        </div>
        <div>
          <p className="text-xs font-bold text-gray-500 dark:text-gray-400">상세</p>
          <p className="font-medium break-all whitespace-pre-wrap">{log.detail ?? "-"}</p>
        </div>
        <div>
          <p className="text-xs font-bold text-gray-500 dark:text-gray-400">IP</p>
          <p className="font-medium tabular-nums">{log.ipAddress ?? "-"}</p>
        </div>
      </div>
    </Dialog>
  );
}
