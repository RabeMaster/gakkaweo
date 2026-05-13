import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useScrollLock } from "@/shared/hooks/useScrollLock";

const escapeStack: (() => void)[] = [];

function handleGlobalEscape(e: KeyboardEvent) {
  if (e.key === "Escape" && escapeStack.length > 0) {
    escapeStack[escapeStack.length - 1]();
  }
}

interface DialogProps {
  isOpen?: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
  maxWidth?: string;
  disableClose?: boolean;
}

export function Dialog({
  isOpen = true,
  onClose,
  title,
  children,
  footer,
  className = "",
  maxWidth = "max-w-sm",
  disableClose = false,
}: DialogProps) {
  const titleId = useId();
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useScrollLock(isOpen);

  useEffect(() => {
    if (!isOpen || disableClose) {
      return;
    }

    const handler = () => {
      onCloseRef.current();
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
  }, [isOpen, disableClose]);

  if (!isOpen) {
    return null;
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 text-black dark:text-white"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !disableClose) {
          onClose();
        }
      }}
    >
      <div
        className={[
          "border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-h-[85vh] flex flex-col mx-4 md:mx-0",
          maxWidth,
          className,
        ].join(" ")}
      >
        <div className="flex items-center justify-between border-b-4 border-black dark:border-white px-4 py-4 md:px-6 md:py-5 shrink-0">
          <h2 id={titleId} className="text-xl font-black">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            disabled={disableClose}
            className={[
              "border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white px-3 py-1 font-black text-lg transition-all duration-100",
              disableClose
                ? "opacity-50 cursor-not-allowed"
                : "shadow-brutal-sm hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]",
            ].join(" ")}
            aria-label="닫기"
          >
            ✕
          </button>
        </div>

        <div className="px-4 py-4 md:px-6 md:py-5 overflow-y-auto flex-1">{children}</div>

        {footer && (
          <div className="flex gap-3 px-4 py-3 md:px-6 md:py-4 border-t-4 border-black dark:border-white shrink-0">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}
