import { useEffect } from "react";

let lockCount = 0;

export function useScrollLock(enabled: boolean) {
  useEffect(() => {
    if (!enabled) {
      return;
    }

    lockCount++;
    if (lockCount === 1) {
      const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
      document.body.style.overflow = "hidden";
      if (scrollbarWidth > 0) {
        document.body.style.paddingRight = `${scrollbarWidth}px`;
      }
    }

    return () => {
      lockCount--;
      if (lockCount === 0) {
        document.body.style.overflow = "";
        document.body.style.paddingRight = "";
      }
    };
  }, [enabled]);
}
