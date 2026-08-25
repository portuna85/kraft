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
    // PERF-BUNDLE-01: 예산 스크립트의 순수 계산부(허용치·청크 분류·판정)도 테스트
    // 대상이다. coverage.include는 src/**로 그대로 둬서 임계값에는 영향이 없다.
    include: ["src/**/*.test.ts", "src/**/*.test.tsx", "scripts/**/*.test.mjs"],
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
      //
      // QA-FE-01(docs/improvement.md): 프론트 API 어댑터 8개(round·community-post·
      // community-report·recommendation·statistics·status-incident·saved-number·
      // community-comment)에 위험 기반 테스트를 추가한 뒤 2026-08-24 재실측
      // (statements 84.97%, branches 81.56%, functions 82.56%, lines 86.5%)에 맞춰
      // 실측 대비 2~3%p 이내로 올렸다(문서 §6 성공 지표).
      thresholds: {
        statements: 82,
        branches: 78,
        functions: 79,
        lines: 83,
      },
    },
  },
});
