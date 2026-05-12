const value = import.meta.env.VITE_API_BASE_URL;

if (!value) {
  throw new Error("VITE_API_BASE_URL 환경변수가 설정되지 않았습니다.");
}

export const API_BASE_URL: string = value;
