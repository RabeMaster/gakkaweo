import { useEffect } from "react";
import type { RefObject } from "react";

export function useClickOutside(
  ref: RefObject<HTMLElement | null>,
  onClose: () => void,
  options: { disabled?: boolean; excludeRefs?: RefObject<HTMLElement | null>[] } = {},
) {
  const { disabled = false, excludeRefs } = options;

  useEffect(() => {
    if (disabled) {
      return;
    }

    function handleMouseDown(e: MouseEvent) {
      const target = e.target as Node;
      if (ref.current && !ref.current.contains(target)) {
        if (excludeRefs?.some((r) => r.current?.contains(target))) {
          return;
        }
        onClose();
      }
    }

    document.addEventListener("mousedown", handleMouseDown);
    return () => {
      document.removeEventListener("mousedown", handleMouseDown);
    };
  }, [ref, onClose, disabled, excludeRefs]);
}
