import { defineConfig, devices } from "@playwright/test";

// 하단 고정 광고(StickyMobileAd)가 CTA·푸터를 실제로 가리는지 검증하려면
// NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY가 baked-in된 빌드가 필요하다(광고 env는 Next
// 빌드 타임에 번들에 박힌다). 다른 두 e2e 트랙(playwright.config.ts, playwright.content.
// config.ts)과 .next/standalone 산출물을 공유하면 서로 덮어써서 로컬에서 트랙을 번갈아
// 돌릴 때 꼬이므로 NEXT_DIST_DIR로 별도 산출물 디렉터리
// (.next-ad-overlay)를 써서 완전히 분리했다. 백엔드는 필요 없다 — StickyMobileAd는
// 루트 레이아웃에 있어 모든 라우트에서 백엔드 상태와 무관하게 mount된다.
export default defineConfig({
  testDir: "./e2e/ad-overlay",
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // KF-10: 실측 데이터 수집(§계측 후 샤드 재조정) — playwright.config.ts와 동일 이유.
  reporter: process.env.CI ? [["github"], ["json", { outputFile: "playwright-timing.json" }]] : "list",
  use: {
    baseURL: "http://127.0.0.1:3105",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "Mobile Chrome", use: { ...devices["Pixel 5"] } },
  ],
  webServer: {
    command: "npm run e2e:serve",
    url: "http://127.0.0.1:3105",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    env: {
      NEXT_DIST_DIR: ".next-ad-overlay",
      KRAFT_BACKEND_INTERNAL_URL: "http://127.0.0.1:59999",
      KRAFT_PUBLIC_BASE_URL: "http://127.0.0.1:3105",
      PORT: "3105",
      // F-P0-12: /ops가 기본 fail-closed로 바뀌어, 이 값이 없으면 /ops 관련 테스트가
      // 전부 404를 받는다 — baseURL 호스트(127.0.0.1)와 일치시켜 허용한다.
      KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
    },
  },
});
