import { useEffect, useRef } from "react";

const escapeStack: (() => void)[] = [];

function handleGlobalEscape(e: KeyboardEvent) {
  if (e.key === "Escape" && escapeStack.length > 0) {
    escapeStack[escapeStack.length - 1]();
  }
}

export function useEscapeStack(onEscape: () => void, enabled: boolean) {
  const ref = useRef(onEscape);
  useEffect(() => {
    ref.current = onEscape;
  });

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const handler = () => {
      ref.current();
    };
    escapeStack.push(handler);

    if (escapeStack.length === 1) {
      document.addEventListener("keydown", handleGlobalEscape);
    }

    return () => {
      const idx = escapeStack.indexOf(handler);
      if (idx !== -1) {
        escapeStack.splice(idx, 1);
      }
      if (escapeStack.length === 0) {
        document.removeEventListener("keydown", handleGlobalEscape);
      }
    };
  }, [enabled]);
}
