import { useEffect, useRef } from "react";

interface ProfileImagePopoverProps {
  hasImage: boolean;
  onChangeClick: () => void;
  onDeleteClick: () => void;
  onClose: () => void;
}

export function ProfileImagePopover({ hasImage, onChangeClick, onDeleteClick, onClose }: ProfileImagePopoverProps) {
  const popoverRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [onClose]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  return (
    <div
      ref={popoverRef}
      className="absolute top-full left-1/2 -translate-x-1/2 mt-2 z-10 border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal-sm"
    >
      <button
        type="button"
        onClick={onChangeClick}
        className="block w-full px-5 py-2.5 text-sm font-bold text-left hover:bg-indigo-50 dark:hover:bg-gray-800 cursor-pointer whitespace-nowrap"
      >
        변경
      </button>
      {hasImage && (
        <button
          type="button"
          onClick={onDeleteClick}
          className="block w-full px-5 py-2.5 text-sm font-bold text-left text-red-500 hover:bg-red-50 dark:hover:bg-gray-800 cursor-pointer whitespace-nowrap border-t-2 border-black dark:border-white"
        >
          삭제
        </button>
      )}
    </div>
  );
}
