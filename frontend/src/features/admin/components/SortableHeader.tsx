import type { ReactNode } from "react";

type Align = "left" | "center" | "right";

type Props = {
  field: string;
  label: ReactNode;
  currentSort: string | undefined;
  onSortChange: (field: string) => void;
  align?: Align;
  className?: string;
};

const ALIGN_CLASS: Record<Align, string> = {
  left: "text-left",
  center: "text-center",
  right: "text-right",
};

const JUSTIFY_CLASS: Record<Align, string> = {
  left: "justify-start",
  center: "justify-center",
  right: "justify-end",
};

export function SortableHeader({ field, label, currentSort, onSortChange, align = "center", className = "" }: Props) {
  const [activeField, activeDir] = currentSort?.split(",") ?? [];
  const isActive = activeField === field;
  const ariaSort = isActive ? (activeDir === "asc" ? "ascending" : "descending") : "none";

  const cellClass = isActive ? "bg-yellow-300 dark:bg-yellow-600" : "hover:bg-yellow-100 dark:hover:bg-yellow-900/30";

  return (
    <th
      scope="col"
      aria-sort={ariaSort}
      className={`${ALIGN_CLASS[align]} ${cellClass} font-black transition-colors ${className}`}
    >
      <button
        type="button"
        onClick={() => onSortChange(field)}
        className={`w-full h-full inline-flex items-center gap-1.5 ${JUSTIFY_CLASS[align]} font-black ${
          isActive ? "underline underline-offset-4" : "hover:underline"
        }`}
        title={isActive ? `${activeDir === "asc" ? "오름차순" : "내림차순"} 정렬 중` : "클릭하여 정렬"}
      >
        <span>{label}</span>
        {isActive ? (
          <span
            className="inline-flex items-center justify-center min-w-[1.25rem] h-5 border-2 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white text-[10px] font-black px-1 leading-none"
            aria-hidden="true"
          >
            {activeDir === "asc" ? "▲" : "▼"}
          </span>
        ) : (
          <span className="text-xs text-gray-400 dark:text-gray-500 leading-none" aria-hidden="true">
            ⇅
          </span>
        )}
      </button>
    </th>
  );
}
