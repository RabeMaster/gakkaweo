const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

/** 상대경로 프로필 URL에 API base URL을 붙인다. 배포 시 같은 도메인이면 이 함수만 제거. */
export function resolveProfileUrl(url: string | null | undefined): string | null {
  if (!url) {
    return null;
  }
  if (url.startsWith("http")) {
    return url;
  }
  return `${API_BASE}${url}`;
}
