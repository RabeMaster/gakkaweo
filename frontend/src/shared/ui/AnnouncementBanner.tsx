import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getActiveAnnouncements } from "@/shared/api/announcements";
import type { ActiveAnnouncementResponse } from "@/shared/api/types";
import { getAnnouncementTypeColor } from "@/shared/config/announcement";
import { STALE_TIME } from "@/shared/config/query";

const STORAGE_KEY = "gakkaweo-dismissed-announcements";

function toDismissKey(a: ActiveAnnouncementResponse): string {
  return `${a.id}_${a.startsAt}`;
}

function loadDismissed(): Set<string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      return new Set(JSON.parse(raw) as string[]);
    }
  } catch {
    /* 무시 */
  }
  return new Set();
}

function saveDismissed(keys: Set<string>) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...keys]));
  } catch {
    /* 무시 */
  }
}

function BannerItem({
  announcement,
  onDismiss,
}: {
  announcement: ActiveAnnouncementResponse;
  onDismiss: (key: string) => void;
}) {
  const colorClass = getAnnouncementTypeColor(announcement.type);

  return (
    <div
      className={`border-4 border-black dark:border-white ${colorClass} shadow-brutal-sm px-5 py-3 flex items-start gap-3`}
    >
      <div className="flex-1 min-w-0">
        <p className="font-black text-sm">{announcement.title}</p>
        {announcement.content && (
          <p className="whitespace-pre-line break-words text-xs font-medium mt-0.5 opacity-80">
            {announcement.content}
          </p>
        )}
      </div>
      <button
        type="button"
        onClick={() => onDismiss(toDismissKey(announcement))}
        aria-label="공지 닫기"
        className="-m-3 p-3 text-lg font-black leading-none shrink-0 hover:opacity-60 transition-opacity"
      >
        &times;
      </button>
    </div>
  );
}

export function AnnouncementBanner() {
  const queryClient = useQueryClient();
  const { data: announcements } = useQuery({
    queryKey: ["announcements", "active"],
    queryFn: getActiveAnnouncements,
    staleTime: STALE_TIME.LONG,
  });

  const [dismissed, setDismissed] = useState<Set<string>>(loadDismissed);

  useEffect(() => {
    function handleAnnouncementEvent() {
      queryClient.invalidateQueries({ queryKey: ["announcements", "active"] });
    }

    window.addEventListener("sse:announcement", handleAnnouncementEvent);
    return () => {
      window.removeEventListener("sse:announcement", handleAnnouncementEvent);
    };
  }, [queryClient]);

  // 활성 공지 키 기준으로 dismissed를 필터링 (삭제된 공지의 오래된 항목 정리)
  const activeKeys = new Set(announcements?.map(toDismissKey) ?? []);
  const effectiveDismissed = new Set([...dismissed].filter((key) => activeKeys.has(key)));
  const visible = announcements?.filter((a) => !effectiveDismissed.has(toDismissKey(a))) ?? [];

  if (visible.length === 0) {
    return null;
  }

  function handleDismiss(key: string) {
    setDismissed((prev) => {
      // 활성 공지 키만 유지 + 새로 닫은 키 추가 (오래된 항목 정리)
      const pruned = new Set([...prev].filter((k) => activeKeys.has(k)));
      pruned.add(key);
      saveDismissed(pruned);
      return pruned;
    });
  }

  return (
    <div className="space-y-2 mb-4">
      {visible.map((a) => (
        <BannerItem key={a.id} announcement={a} onDismiss={handleDismiss} />
      ))}
    </div>
  );
}
