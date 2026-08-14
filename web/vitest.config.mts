import { fileURLToPath } from "node:url";

import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./tests/setup.ts"],
    // 테스트는 소스 옆에 co-locate 한다 — 현행의 평면 __tests__/(82개)는 대응 소스를
    // 찾기 어려웠다(L-3).
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    coverage: {
      provider: "v8",
      reportsDirectory: "./coverage",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/app/**/layout.tsx", "src/app/**/page.tsx"],
      // M-07: 커버리지가 측정만 되고 아무것도 강제하지 않았다 — 큰 기능을 테스트 없이
      // 추가해도 CI가 조용히 통과했다. 2026-08-12 실측(statements 74.08%, branches
      // 71.48%, functions 71.23%, lines 75.69%)에서 각각 몇 포인트 낮춘 보수적 하한을
      // 둔다 — 레거시처럼 실측치에 딱 맞추면 사소한 리팩터링에도 깨진다. 실제 커버리지가
      // 여유 있게 올라가면 하한도 의도적으로 올린다(ratchet).
      thresholds: {
        statements: 70,
        branches: 65,
        functions: 65,
        lines: 70,
      },
    },
  },
});
