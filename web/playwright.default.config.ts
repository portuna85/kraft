import { defineConfig, devices } from "@playwright/test";

/**
 * default 트랙 — 픽스처 백엔드를 상시 가동한 주요 사용자 흐름
 *
 * 레거시의 default(백엔드 없음)/content(픽스처 있음) 2트랙 분리를 따르지 않는다.
 * `web/`은 핵심 데이터 실패 시 200 폴백이 아니라 5xx로 전파하는 설계라(§6.5)
 * 대부분 페이지가 의미 있게 렌더되려면 어차피 픽스처 백엔드가 필요하다. 이 트랙은
 * 픽스처를 상시 가동하고, T-20(핵심 데이터 실패→5xx)만 `/__test__/fail` 제어
 * 엔드포인트로 해당 테스트 안에서 개별적으로 백엔드를 일시 중단시켜 처리한다
 * (Phase 7 계획, [[project_fe_rewrite_phase5_progress]] 참고).
 */
const PORT = 3111;
const BACKEND_PORT = 4111;
const BASE_URL = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e/default",
  fullyParallel: false,
  // 픽스처 백엔드가 저장 번호·추천 이력·강제 실패 상태를 파일 간에 공유한다 — 여러
  // 워커가 동시에 돌면 한 파일의 "장애 강제"가 다른 파일의 렌더 확인과 겹친다.
  workers: 1,
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
      // 정적 라우트로 준비 상태를 확인한다 — BASE_URL(홈)로 확인하면 Playwright의
      // 준비 폴링 자체가 회차 조회를 성공시켜 revalidate 캐시를 데워 버린다. 그러면
      // T-20(핵심 데이터 실패→5xx) 테스트가 실제로 실패를 강제해도 이미 캐시된 성공
      // 응답이 계속 나가 절대 재현되지 않는다(§13.6 stale-while-error).
      url: `${BASE_URL}/robots.txt`,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: {
        PORT: String(PORT),
        NEXT_DIST_DIR: ".next-default",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
