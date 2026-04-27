import { useCallback, useState } from "react";

export function useSortState(initial?: string) {
  const [sort, setSort] = useState<string | undefined>(initial);

  const toggleSort = useCallback((field: string) => {
    setSort((prev) => {
      if (!prev) {
        return `${field},desc`;
      }
      const [prevField, prevDir] = prev.split(",");
      if (prevField === field) {
        return `${field},${prevDir === "desc" ? "asc" : "desc"}`;
      }
      return `${field},desc`;
    });
  }, []);

  return { sort, toggleSort };
}
