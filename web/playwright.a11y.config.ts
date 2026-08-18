import { defineConfig, devices } from "@playwright/test";

/**
 * a11y 트랙 — axe 자동 검사 + 키보드만으로 핵심 흐름 완주 (T-23·24)
 *
 * 레거시는 접근성 스캔을 default/content 트랙 안에 끼워 뒀지만, 여기서는 §21.4가
 * 명시적으로 a11y를 독립 트랙으로 요구해 분리한다. default 트랙과 같은 픽스처
 * 백엔드 구성을 그대로 재사용한다(포트만 다르게 분리 — 트랙 간 서버가 겹치지 않게).
 */
const PORT = 3112;
const BACKEND_PORT = 4112;
const BASE_URL = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e/a11y",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: { baseURL: BASE_URL, trace: "on-first-retry" },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    // 부가 항목(docs/improvement.md KF-26) — axe 스캔이 지금까지 1280×720
    // 데스크톱 뷰포트에서만 돌아, 1024px 미만에서만 마운트되는 하단 탭바나
    // 데스크톱에서는 렌더되지 않는 광고 닫기 버튼 같은 모바일 전용 요소가 한 번도
    // 스캔되지 않았다. `e2e/a11y/*.spec.ts`를 그대로 재사용해 모바일 뷰포트에서도
    // axe가 돌게 한다.
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
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
        NEXT_DIST_DIR: ".next-a11y",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
