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
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
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
