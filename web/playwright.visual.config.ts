import { defineConfig, devices } from "@playwright/test";

// Phase 0 기준선(docs/improvement.md §17 Phase 0, §16.3): 지금까지 web/e2e/css-regression.spec.ts는
// computed-style 스냅샷이었을 뿐 실제 픽셀 스크린샷 회귀는 없었다. 이 설정은 대표 라우트를
// 실콘텐츠 상태(픽스처 백엔드, playwright.content.config.ts와 동일한 패턴)로 렌더해 픽셀
// 스크린샷 베이스라인을 고정한다. 다른 트랙과 포트가 겹치지 않게 앱 3102, 픽스처 백엔드
// 4102를 쓴다. Chromium 계열만 다룬다 — Firefox/WebKit 교차 브라우저 스크린샷은
// docs/improvement.md §17 Phase 6("실제 Chrome·Firefox·Safari 검증")의 관심사라 이번
// 범위 밖이다.
const FIXTURE_BACKEND_URL = "http://127.0.0.1:4102";

export default defineConfig({
  testDir: "./e2e/visual",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  // maxDiffPixelRatio를 느슨하게 주면(0.02 등) 전체 페이지 스크린샷에서 강조색(accent)
  // 하나가 완전히 바뀌어도 잡히지 않는 걸 실제로 확인했다 — Playwright 기본값(비율 제한
  // 없음, 픽셀 단위 threshold 0.2)을 그대로 쓴다. 베이스라인은 CI와 동일한 Linux/Chromium
  // 조합(Docker mcr.microsoft.com/playwright 이미지)에서만 생성해야 폰트 렌더링 차이로
  // 인한 오탐을 피할 수 있다.
  use: {
    baseURL: "http://127.0.0.1:3102",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "chromium",      use: { ...devices["Desktop Chrome"] } },
    { name: "Mobile Chrome", use: { ...devices["Pixel 5"] } },
    { name: "Tablet",        use: { browserName: "chromium", ...devices["iPad (gen 7)"] } },
  ],
  webServer: [
    {
      command: "node e2e/fixtures/backend.mjs",
      url: FIXTURE_BACKEND_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 10_000,
      env: {
        E2E_FIXTURE_BACKEND_PORT: "4102",
      },
    },
    {
      command: "npm run e2e:serve",
      url: "http://127.0.0.1:3102",
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      env: {
        NEXT_DIST_DIR: ".next-visual",
        KRAFT_BACKEND_INTERNAL_URL: FIXTURE_BACKEND_URL,
        KRAFT_PUBLIC_BASE_URL: "http://127.0.0.1:3102",
        PORT: "3102",
      },
    },
  ],
});
