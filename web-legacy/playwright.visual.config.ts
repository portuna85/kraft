import { defineConfig, devices } from "@playwright/test";

// 시각 회귀 기준선: 지금까지 web/e2e/css-regression.spec.ts는
// computed-style 스냅샷이었을 뿐 실제 픽셀 스크린샷 회귀는 없었다. 이 설정은 대표 라우트를
// 실콘텐츠 상태(픽스처 백엔드, playwright.content.config.ts와 동일한 패턴)로 렌더해 픽셀
// 스크린샷 베이스라인을 고정한다. 다른 트랙과 포트가 겹치지 않게 앱 3102, 픽스처 백엔드
// 4102를 쓴다. Chromium 계열만 다룬다 — Firefox/WebKit 교차 브라우저 스크린샷은
// 실제 Chrome·Firefox·Safari 검증은 별도 브라우저 호환성 점검의 관심사라 이번
// 범위 밖이다.
const FIXTURE_BACKEND_URL = "http://127.0.0.1:4102";

export default defineConfig({
  testDir: "./e2e/visual",
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // KF-10: 실측 데이터 수집(§계측 후 샤드 재조정) — playwright.config.ts와 동일 이유.
  reporter: process.env.CI ? [["github"], ["json", { outputFile: "playwright-timing.json" }]] : "list",
  // 두 극단을 모두 실측으로 확인했다: maxDiffPixelRatio 0.02(2%)는 전체 페이지 스크린샷에서
  // 강조색(accent)이 완전히 바뀌어도 못 잡았고, 반대로 비율 제한을 아예 없애면(Playwright
  // 기본값) 로컬(Docker mcr.microsoft.com/playwright 이미지)에서 만든 베이스라인이 실제
  // GitHub Actions 러너(같은 OS·브라우저 버전이어도 물리 머신이 다름)에서 몇 픽셀 단위
  // 안티에일리어싱 노이즈로 매번 깨졌다(실제 PR #70 CI 실행에서 확인). 노이즈보다는
  // 넉넉하고 실제 회귀(수천~수만 픽셀)보다는 훨씬 작은 값으로 둔다.
  expect: {
    toHaveScreenshot: { maxDiffPixelRatio: 0.002 },
  },
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
        // F-P0-12: /ops가 기본 fail-closed로 바뀌어, 이 값이 없으면 /ops 관련 테스트가
        // 전부 404를 받는다 — baseURL 호스트(127.0.0.1)와 일치시켜 허용한다.
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
