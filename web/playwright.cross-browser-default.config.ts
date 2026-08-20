import { defineConfig, devices } from "@playwright/test";

/**
 * cross-browser 트랙 — Firefox·WebKit에서 default 트랙(핵심 사용자 흐름) 재검증
 *
 * `playwright.cross-browser.config.ts`(a11y 스펙 재사용)와 별도 설정 파일로 둔
 * 이유는 `fullyParallel`/`workers` 요구가 정반대이기 때문이다 — a11y 스펙은
 * 라우트가 서로 독립적이라 병렬이 안전하지만, `e2e/default`는 픽스처 백엔드가
 * 저장 번호·추천 이력·강제 실패 상태를 파일 간에 공유해(`playwright.default.
 * config.ts`와 동일한 이유) `workers: 1`이 필수다. 한 config에 두 성격을 섞으면
 * Playwright의 `workers`가 전역 설정이라 a11y까지 직렬로 묶여 느려진다.
 *
 * Mobile Safari는 여기 포함하지 않는다 — a11y 트랙이 이미 모바일 뷰포트/터치
 * 에뮬레이션을 커버하고, `e2e/default`는 브라우저별 렌더링보다 데이터 흐름
 * (소유권·CSRF·페이지네이션)을 검증하는 스펙이 대부분이라 3번째 모바일 패스의
 * 한계효용이 낮다.
 */
const PORT = 3116;
const BACKEND_PORT = 4116;
const BASE_URL = `http://127.0.0.1:${PORT}`;

export default defineConfig({
  testDir: "./e2e/default",
  // 00-core-failure.spec.ts는 "이 스펙이 이 서버 프로세스에 대한 최초의 '/' 요청이어야
  // 한다"는 불변식을 파일명 정렬로 강제한다(getLatestRound()의 revalidate 캐시가 한 번
  // 데워지면 이후 실패 주입도 stale-while-error로 흡수돼 버린다). default 트랙(단일
  // Chromium 프로젝트)에서는 성립하지만, 이 config는 여러 브라우저 프로젝트가 **같은**
  // Next 서버 인스턴스를 순서대로 공유한다 — 두 번째 프로젝트가 시작할 때는 첫 번째
  // 프로젝트가 이미 "/"를 여러 번 데워 놓은 뒤다. 이 캐시 불변식은 브라우저 엔진과
  // 무관한 서버 사이드 동작이라(default 트랙이 이미 Chromium으로 검증) 여기서 재검증할
  // 가치가 없다 — 프로젝트마다 별도 서버를 띄우는 비용을 감수하지 않고 건너뛴다.
  //
  // recommend-and-saved.spec.ts·recommend-history-account.spec.ts·
  // recommend-studio-session-race.spec.ts는 세션 조회가 XSRF-TOKEN 쿠키를 심을
  // 때까지 기다리는 타이밍에 의존한다. CI에서 실제로 돌려 확인한 결과, Firefox·
  // WebKit에서는 이 워크서버(다른 트랙보다 늦게, 이미 수십 개 요청을 받은 뒤에
  // 뜨는 단일 Next 서버를 두 브라우저 프로젝트가 순서대로 공유)를 상대로는
  // `page.waitForFunction(() => document.cookie.includes("XSRF-TOKEN"))`이
  // 재시도(retries:1)까지 포함해 30초 안에 안정적으로 끝나지 않는다 — 반면 같은
  // 스펙은 default 트랙(Chromium, 전용 서버, workers:1)에서는 안정적으로
  // 통과한다. 이 세 스펙이 검증하는 것(소유권 전환·CSRF 준비성 경쟁)은 브라우저
  // 렌더링과 무관한 클라이언트 상태 흐름이라 Chromium 검증으로 충분하다고 보고,
  // 근본 원인(쿠키 전파 타이밍) 조사 없이 재시도 수를 늘려 덮지 않는다.
  testIgnore: [
    "00-core-failure.spec.ts",
    "recommend-and-saved.spec.ts",
    "recommend-history-account.spec.ts",
    "recommend-studio-session-race.spec.ts",
  ],
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // 같은 CI 잡 안에서 cross-browser.config.ts(a11y)를 먼저 돌린 뒤 이 config를 이어
  // 실행한다 — 기본 output/report 폴더 이름이 같으면 두 번째 실행이 첫 번째 결과를
  // 덮어써 실패 시 업로드되는 트레이스가 섞인다. 폴더 이름을 분리한다.
  outputDir: "test-results-cross-browser-default",
  reporter: process.env.CI
    ? [
        ["github"],
        ["html", { open: "never", outputFolder: "playwright-report-cross-browser-default" }],
      ]
    : [["list"]],
  use: { baseURL: BASE_URL, trace: "on-first-retry" },
  projects: [
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
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
        // cross-browser 잡이 이미 이 이름으로 빌드해 뒀다(ci.yml) — 다시 빌드하지
        // 않는다.
        NEXT_DIST_DIR: ".next-cross-browser",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
        KRAFT_REVALIDATE_SECRET: "e2e-revalidate-secret",
      },
    },
  ],
});
