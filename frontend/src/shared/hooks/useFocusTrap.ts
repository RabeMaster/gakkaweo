import { useEffect, type RefObject } from "react";

const FOCUSABLE =
  "button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex='-1'])";

/** 열린 대화상자 안에서 키보드 초점을 유지하고 종료 후 실행 버튼으로 복원합니다. */
export function useFocusTrap(active: boolean, panel: RefObject<HTMLElement | null>) {
  useEffect(() => {
    if (!active || !panel.current) {
      return;
    }
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const root = panel.current;
    const controls = () =>
      Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
        (element) => element.getClientRects().length > 0,
      );
    (controls()[0] ?? root).focus();
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== "Tab") {
        return;
      }
      const elements = controls();
      const first = elements[0] ?? root;
      const last = elements.at(-1) ?? root;
      if (event.shiftKey && (document.activeElement === first || !root.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !root.contains(document.activeElement))) {
        event.preventDefault();
        first.focus();
      }
    }
    root.addEventListener("keydown", handleKeyDown);
    return () => {
      root.removeEventListener("keydown", handleKeyDown);
      if (previous?.isConnected) {
        previous.focus();
      }
    };
  }, [active, panel]);
}
