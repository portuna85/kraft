import { defineConfig, devices } from "@playwright/test";

/**
 * responsive 트랙 — 경계 뷰포트·회전·확대 프로그래매틱 단언 (Phase 1)
 *
 * baseline(visual 트랙)이 "픽셀이 달라졌는가"를 보는 것과 달리, 여기서는 특정
 * 뷰포트 조건에서 문서 overflow·hit area·폰트 크기·고정 UI 가림 같은 계약을
 * `e2e/lib/responsive-assertions.ts`로 직접 단언한다. 뷰포트는 프로젝트가 아니라
 * 각 테스트가 `test.use({ viewport })`로 지정한다 — 라우트 수 × 뷰포트 수만큼
 * 시각 기준 이미지를 늘리지 않기 위해서다(docs/improvement_codex_fe.md §8.1
 * PR boundary 계층, docs/improvement_claude_fe.md §7.3 3단계).
 */
const PORT = 3115;
const BACKEND_PORT = 4115;
const BASE_URL = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e/responsive",
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
        NEXT_DIST_DIR: ".next-responsive",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
