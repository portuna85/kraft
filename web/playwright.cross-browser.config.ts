import { defineConfig, devices } from "@playwright/test";

/**
 * cross-browser 트랙 — Firefox·WebKit에서 핵심 흐름 + 접근성 재검증
 * (Phase 7 계획)
 *
 * Chromium 전용 트랙(default·a11y)이 빠른 회귀 게이트를 맡고, 여기서는 실제
 * 브라우저 호환성만 본다 — 그래서 스펙을 새로 쓰지 않고 기존 a11y 트랙의 스펙
 * (`e2e/a11y/*.spec.ts`)을 그대로 재사용한다. axe 스캔은 렌더링 차이(폰트·레이아웃)를
 * 브라우저마다 다르게 잡아낼 수 있고, 키보드 흐름은 포커스 이동 구현이 엔진마다
 * 갈리는 지점이라 재검증 가치가 크다(web-legacy의 같은 구성과 동일한 전략).
 */
const PORT = 3114;
const BACKEND_PORT = 4114;
const BASE_URL = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e/a11y",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: { baseURL: BASE_URL, trace: "on-first-retry" },
  projects: [
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
  webServer: [
    {
      command: "node e2e/fixtures/backend.mjs",
      url: `http://127.0.0.1:${BACKEND_PORT}/api/v1/rounds/latest`,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
      env: { PORT: String(BACKEND_PORT) },
    },
    {
      command: "node scripts/start-standalone.mjs",
      url: `${BASE_URL}/robots.txt`,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: {
        PORT: String(PORT),
        NEXT_DIST_DIR: ".next-cross-browser",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
