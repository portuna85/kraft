import { defineConfig, devices } from "@playwright/test";

/**
 * visual 트랙 — 픽셀 스크린샷 회귀 (T-25·26, improvement_fe.md §21.5)
 *
 * 베이스라인은 로컬에서 만들지 않는다. 로컬(Windows)과 CI(Linux, mcr.microsoft.com/
 * playwright 이미지)는 폰트 렌더링·안티에일리어싱이 달라, 로컬에서 만든 베이스라인은
 * CI에서 항상 깨진다 — 실제로 web-legacy에서 한 번 겪은 문제다
 * ([[project_visual_baseline_ci_adopt]]). `e2e/visual/baseline.spec.ts-snapshots/`의
 * 베이스라인은 2026-08-10 CI 실행(run 31362445957)이 만든 actual.png를 그대로 채택한
 * 것이다 — 갱신할 때도 로컬 결과를 커밋하지 말고 이 절차를 반복한다.
 *
 * maxDiffPixelRatio 0.002는 web-legacy의 playwright.visual.config.ts에서 실측으로 정한
 * 값을 그대로 가져왔다 — 0.02는 강조색 전체 변경도 못 잡았고, 0(기본값)은 같은 CI
 * 러너에서도 안티에일리어싱 노이즈로 매번 깨졌다.
 */
const PORT = 3113;
const BACKEND_PORT = 4113;
const BASE_URL = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e/visual",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  expect: {
    toHaveScreenshot: { maxDiffPixelRatio: 0.002 },
  },
  use: { baseURL: BASE_URL, trace: "retain-on-failure" },
  // 3개 뷰포트 — improvement_fe.md §25.8, web-legacy(playwright.visual.config.ts)와
  // 같은 3개 프로젝트다. Desktop 하나만으로는 반응형 레이아웃(모바일 하단 탭바, 태블릿
  // 사이드바 유무 전환 등)의 시각 회귀를 못 잡는다.
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "Mobile Chrome", use: { ...devices["Pixel 5"] } },
    { name: "Tablet", use: { browserName: "chromium", ...devices["iPad (gen 7)"] } },
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
        NEXT_DIST_DIR: ".next-visual",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
