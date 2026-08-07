import { defineConfig, devices } from "@playwright/test";

/**
 * proxy 트랙 — CSP·nonce·/ops 호스트 게이트 (T-21·T-22)
 *
 * 다른 트랙과 분리한 이유: 이 트랙은 KRAFT_OPS_ALLOWED_HOST를 **의도적으로 비운 채**
 * 앱을 띄워야 한다(미설정 시 404인지 확인해야 하므로). 같은 서버를 공유하면 그 조건을
 * 만들 수 없다.
 */
const PORT = 3110;
const BACKEND_PORT = 4110;
const BASE_URL = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e/proxy",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: { baseURL: BASE_URL, trace: "on-first-retry" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    // 핵심 데이터 실패는 5xx로 전파되므로(§6.5), 백엔드 없이는 홈이 렌더되지 않는다.
    // 하이드레이션을 확인하려면 렌더되는 화면이 있어야 해서 픽스처를 함께 띄운다.
    {
      command: "node e2e/fixtures/backend.mjs",
      url: `http://127.0.0.1:${BACKEND_PORT}/api/v1/rounds/latest`,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
      env: { PORT: String(BACKEND_PORT) },
    },
    {
      command: "node scripts/start-standalone.mjs",
      url: BASE_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: {
        PORT: String(PORT),
        NEXT_DIST_DIR: ".next-proxy",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        // 비워 둔다 — fail-closed 검증의 전제다.
        KRAFT_OPS_ALLOWED_HOST: "",
      },
    },
  ],
});
