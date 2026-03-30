import { Button } from "@/shared/ui/Button";

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <div className="flex items-center gap-2 justify-center">
      <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        이전
      </Button>
      <span className="text-sm font-bold tabular-nums">
        {page + 1} / {totalPages}
      </span>
      <Button size="sm" variant="secondary" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        다음
      </Button>
    </div>
  );
}
