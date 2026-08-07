import { defineConfig, devices } from "@playwright/test";

// playwright.config.ts(기본 설정)는 백엔드가 없다는 전제로 에러 경계·폴백 UI를
// 검증한다(stats-family.spec.ts, status.spec.ts 등). 이 설정은 그 반대 — 서버 컴포넌트가
// 백엔드를 직접 호출하는 페이지(/, /frequency, /stats, /companion)가 실제 콘텐츠를
// 렌더한 상태에서만 검증 가능한 것(반응형 오버플로 등)을 다룬다. e2e/fixtures/backend.mjs
// 경량 픽스처를 띄우고 앱이 그걸 바라보게 한다.
//
// 기본 설정과 앱/백엔드 포트를 겹치지 않게 분리했다(앱 3100→3101, 백엔드 59999→4101).
// 두 설정 모두 같은 .next/standalone 빌드 산출물을 공유하므로, 로컬에서 두 트랙을
// 번갈아 돌릴 때는 트랙 전환 전에 `npm run build`를 다시 실행해야 한다(CI에서는 잡마다
// 별도 체크아웃+빌드라 문제없음).
const FIXTURE_BACKEND_URL = "http://127.0.0.1:4101";

export default defineConfig({
  testDir: "./e2e/content",
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // KF-10: 실측 데이터 수집(§계측 후 샤드 재조정) — playwright.config.ts와 동일 이유.
  reporter: process.env.CI ? [["github"], ["json", { outputFile: "playwright-timing.json" }]] : "list",
  use: {
    baseURL: "http://127.0.0.1:3101",
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
    },
    {
      command: "npm run e2e:serve",
      url: "http://127.0.0.1:3101",
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
      env: {
        NEXT_DIST_DIR: ".next-content",
        KRAFT_BACKEND_INTERNAL_URL: FIXTURE_BACKEND_URL,
        KRAFT_PUBLIC_BASE_URL: "http://127.0.0.1:3101",
        PORT: "3101",
        // F-P0-12: /ops가 기본 fail-closed로 바뀌어, 이 값이 없으면 /ops 관련 테스트가
        // 전부 404를 받는다 — baseURL 호스트(127.0.0.1)와 일치시켜 허용한다.
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
