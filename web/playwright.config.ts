import { defineConfig, devices } from "@playwright/test";

// 백엔드/DB 없이도 돌아가는 스모크 E2E. 클라이언트 컴포넌트(/recommend, /saved)는
// 브라우저 fetch를 라우트 모킹으로 가로채 검증하고, 서버 컴포넌트 페이지(/, /frequency 등)는
// 백엔드가 없다는 전제로 에러 경계(error.tsx)·폴백 UI가 정상 렌더되는지를 검증한다
// (stats-family.spec.ts, status.spec.ts 등). responsive.spec.ts의 오버플로 검사도 현재
// 이 상태(에러 화면) 기준이다 — 실콘텐츠 상태의 오버플로는 픽스처 백엔드를 쓰는
// playwright.content.config.ts(e2e/content/**)가 별도로 검증한다.
export default defineConfig({
  testDir: "./e2e",
  // e2e/content/**(픽스처 백엔드 전제, playwright.content.config.ts)·e2e/ad-overlay/**
  // (광고 env 빌드 전제, playwright.ad-overlay.config.ts)·e2e/visual/**(픽스처 백엔드 전제 +
  // 스크린샷 베이스라인, playwright.visual.config.ts)는 각각 전용 설정에서만 돌린다 —
  // 이 설정(백엔드 없음, 광고 env 없음)으로 돌리면 항상 실패한다.
  testIgnore: ["content/**", "ad-overlay/**", "visual/**"],
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://127.0.0.1:3100",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "chromium",      use: { ...devices["Desktop Chrome"] } },
    { name: "Mobile Chrome", use: { ...devices["Pixel 5"] } },
    { name: "Tablet",        use: { browserName: "chromium", ...devices["iPad (gen 7)"] } },
  ],
  webServer: {
    // standalone 빌드 산출물(Dockerfile과 동일)을 그대로 띄운다. `npm run build`를
    // 먼저 실행해 .next/standalone이 있어야 한다.
    command: "npm run e2e:serve",
    url: "http://127.0.0.1:3100",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    env: {
      // 백엔드 없이도 빠르게 실패하도록 즉시 거부되는 루프백 포트를 가리킨다.
      KRAFT_BACKEND_INTERNAL_URL: "http://127.0.0.1:59999",
      KRAFT_PUBLIC_BASE_URL: "http://127.0.0.1:3100",
      // scripts/start-standalone.mjs의 기본 포트(3000, npm start/Docker와 동일)에
      // 의존하지 않도록 E2E 전용 포트를 명시한다.
      PORT: "3100",
      // F-P0-12: /ops가 기본 fail-closed로 바뀌어, 이 값이 없으면 /ops 관련 테스트가
      // 전부 404를 받는다 — baseURL 호스트(127.0.0.1)와 일치시켜 허용한다.
      KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
    },
  },
});
