import { useState, useRef, useEffect, useCallback, type ReactNode } from "react";
import { useClickOutside } from "@/shared/hooks/useClickOutside";

type TabKey = "ranking" | "hint";

interface Tab {
  key: TabKey;
  label: string;
  content: ReactNode;
}

interface MobileSideSheetProps {
  tabs: Tab[];
}

export function MobileSideSheet({ tabs }: MobileSideSheetProps) {
  const [activeTab, setActiveTab] = useState<TabKey | null>(null);
  const [isClosing, setIsClosing] = useState(false);
  const drawerRef = useRef<HTMLDivElement>(null);
  const isOpen = activeTab !== null;

  const handleClose = useCallback(() => {
    if (isClosing) {
      return;
    }
    setIsClosing(true);
  }, [isClosing]);

  useClickOutside(drawerRef, handleClose, { disabled: !isOpen || isClosing });

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    document.body.style.overflow = "hidden";

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        handleClose();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = "";
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, handleClose]);

  function handleAnimationEnd() {
    if (isClosing) {
      setActiveTab(null);
      setIsClosing(false);
    }
  }

  function handleTabClick(key: TabKey) {
    if (isClosing) {
      return;
    }
    if (activeTab === key) {
      handleClose();
    } else {
      setActiveTab(key);
    }
  }

  const activeContent = tabs.find((t) => t.key === activeTab)?.content;
  const activeLabel = tabs.find((t) => t.key === activeTab)?.label;

  return (
    <div className="md:hidden">
      {!isOpen && (
        <div className="fixed right-0 top-[38%] -translate-y-1/2 z-40 flex flex-col border-l-4 border-y-4 border-black dark:border-white">
          {tabs.map((tab, i) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={[
                "bg-white dark:bg-gray-950 text-black dark:text-white px-2 py-2.5 font-black text-[10px] transition-colors duration-100 hover:bg-yellow-300 hover:text-black active:bg-black active:text-white dark:active:bg-white dark:active:text-black",
                i > 0 ? "border-t-2 border-black dark:border-white" : "",
              ].join(" ")}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}

      {(isOpen || isClosing) && (
        <div className="fixed inset-0 z-50 bg-black/40">
          <div
            ref={drawerRef}
            className={`absolute top-0 right-0 bottom-0 w-72 border-l-4 border-black dark:border-white bg-white dark:bg-gray-950 overflow-y-auto ${isClosing ? "animate-slide-out-right" : "animate-slide-in-right"}`}
            onAnimationEnd={handleAnimationEnd}
          >
            <div className="flex items-center justify-between border-b-4 border-black dark:border-white px-4 py-3">
              <h2 className="text-lg font-black">{activeLabel}</h2>
              <button
                type="button"
                onClick={handleClose}
                className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white px-2 py-0.5 font-black text-sm shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]"
                aria-label="닫기"
              >
                ✕
              </button>
            </div>

            <div className="flex border-b-4 border-black dark:border-white">
              {tabs.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => handleTabClick(tab.key)}
                  className={[
                    "flex-1 py-2 text-sm font-black text-center transition-colors",
                    activeTab === tab.key ? "bg-yellow-300 text-black" : "text-black dark:text-white",
                    tab.key === "ranking" ? "border-r-2 border-black dark:border-white" : "",
                  ].join(" ")}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <div className="p-4">{activeContent}</div>
          </div>
        </div>
      )}
    </div>
  );
}
