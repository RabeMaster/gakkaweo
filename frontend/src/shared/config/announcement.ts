export const ANNOUNCEMENT_TYPE_LABELS: Record<string, string> = {
  INFO: "안내",
  MAINTENANCE: "점검",
  WARNING: "경고",
};

export const ANNOUNCEMENT_TYPE_COLORS: Record<string, string> = {
  INFO: "bg-blue-300 text-black",
  MAINTENANCE: "bg-orange-300 text-black",
  WARNING: "bg-red-500 text-white",
};

export function getAnnouncementTypeLabel(type: string): string {
  return ANNOUNCEMENT_TYPE_LABELS[type] ?? type;
}

export function getAnnouncementTypeColor(type: string): string {
  return ANNOUNCEMENT_TYPE_COLORS[type] ?? "bg-gray-300 text-black";
}
