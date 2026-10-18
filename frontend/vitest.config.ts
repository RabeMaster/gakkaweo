import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

// 화면을 렌더링하지 않는 함수·데이터 처리 단위 테스트를 Node.js에서 실행합니다.
// 화면 빌드에 쓰는 React·Tailwind 플러그인은 이 테스트 설정에서 제외합니다.
export default defineConfig({
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  test: { environment: "node", pool: "threads" },
});
