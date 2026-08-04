import { defineConfig } from "vitest/config";
import path from "path";

export default defineConfig({
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/__tests__/setup.ts"],
    exclude: ["**/node_modules/**", "**/e2e/**"],
    coverage: {
      provider: "v8",
      // Vitest 4의 v8 provider는 include에 매칭되는 모든 파일을 항상 분모에 포함한다
      // (예전 all:true가 기본 동작이 됨 — 별도 옵션은 더 이상 존재하지 않는다).
      include: ["src/**/*.{ts,tsx}"],
      // src/app/**는 Next.js 페이지·레이아웃·라우트 핸들러 구성 코드로, RSC/스트리밍 등
      // 실제 Next 런타임 없이는 vitest+jsdom으로 의미 있게 단위 테스트하기 어렵다 —
      // e2e(Playwright)가 담당하는 영역이라 커버리지 분모에서 제외한다.
      exclude: ["**/node_modules/**", "**/e2e/**", "**/*.config.*", "src/__tests__/**", "src/lib/api.ts", "src/lib/logger.ts", "src/app/**"],
      // all:true를 켜기 전에는 어떤 테스트도 import하지 않은 파일이 분모에서 아예
      // 빠져 있어 커버리지 숫자가 실제보다 부풀려져 있었다. all:true로 정직한 분모를
      // 쓰는 대신, 여기 임계값은 "지금 실측치"가 아니라 "이 아래로는 떨어지면 안 되는
      // 바닥선"이다 — 실측치가 이보다 높아졌다고 해서 자동으로 올려 잡지 않는다
      // (실측치는 npm run test:coverage로 직접 확인).
      thresholds: {
        lines: 65,
        statements: 65,
        functions: 60,
        branches: 58,
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
