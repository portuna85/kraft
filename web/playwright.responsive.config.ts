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
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    // KF-03(docs/improvement.md): CSS Grid의 automatic minimum size 처리가
    // Firefox에서만 달라 `/stats` 320px가 실제로 가로 오버플로된다(scrollWidth 338
    // vs clientWidth 320). Chromium 전용 커버리지로는 이걸 못 잡는다. responsive
    // 트랙 전체를 firefox로 재실행하면 CI 비용이 두 배가 되므로
    // `document-overflow.spec.ts`에만 스코프한다 — 이 파일이 이미 `/stats`를 포함한
    // 라우트×폭 전체 조합을 스윕하므로 새 스펙 코드 없이 firefox 프로젝트 추가만으로
    // 기존 `/stats@320` 케이스가 red가 된다.
    // RSP-13(docs/improvement.md): KF-03의 원인은 Firefox의 grid automatic
    // minimum size 처리 차이였고 해법은 `.card { min-width: 0 }`이었다. RSP-01이
    // `@container`를 도입하면서 `container-type: inline-size`가 `contain:
    // inline-size`를 걸어 격리 경계가 하나 더 생겼다 — 그것도 `/saved`의
    // `<section>`이라는 **grid 아이템** 위에. 브라우저 간 구현 차이가 다시 드러날
    // 수 있는 정확히 그 지점이라, RSP-13이 미리 적어 둔 재평가 조건에 해당한다.
    {
      name: "firefox-overflow",
      testMatch: /(document-overflow|container-squeeze)\.spec\.ts/,
      use: { ...devices["Desktop Firefox"] },
    },
    // RSP-10(docs/improvement.md): Desktop Chrome은 뷰포트를 390px로 줄여도
    // `hasTouch: false`라 `(hover: hover) and (pointer: fine)`을 **참으로** 평가한다.
    // 그 미디어 특성은 뷰포트 크기가 아니라 `hasTouch`/`isMobile`에서 오기 때문이다.
    // 즉 코드베이스 12곳(8개 파일)의 hover 가드 블록이 지금까지 전부 활성 상태로만
    // 검증돼 왔고, **실제 모바일 사용자가 보는 CSS는 이 트랙에서 한 번도 렌더된 적이
    // 없다.** 특히 lotto-ball.module.css:24와 number-grid.module.css:34의
    // `scale(1.05)`는 요소의 실측 사각형을 직접 바꾸므로, `assertMinHitArea`가
    // 터치 기기에는 존재하지 않는 확대 상태를 재고 있었을 수 있다.
    //
    // firefox-overflow와 같은 이유로 트랙 전체를 재실행하지 않고, 포인터 판정이
    // 실제로 의미 있는 스펙에만 스코프한다. 와일드카드가 아니라 파일명을 끝까지
    // 못박는 이유가 둘 있다.
    //   - touch-target-stretched-link.spec.ts는 `test.fail(true, …)`로 선언된
    //     의도적 red 문서화 테스트다. 와일드카드로 끌어들이면 터치 프로젝트에서
    //     우연히 통과할 때 "passed unexpectedly"로 하드 실패한다.
    //   - fixed-ui-toast-safe-area.spec.ts는 CSSOM 규칙을 파싱하는 스펙이라
    //     포인터 종류와 무관하다 — 재실행해도 얻는 것이 없다.
    {
      name: "mobile-chromium",
      testMatch: /(touch-target|form-controls|fixed-ui)\.spec\.ts$/,
      use: { ...devices["Pixel 7"] },
    },
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
        NEXT_DIST_DIR: ".next-responsive",
        KRAFT_PUBLIC_BASE_URL: BASE_URL,
        KRAFT_BACKEND_INTERNAL_URL: `http://127.0.0.1:${BACKEND_PORT}`,
        KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
      },
    },
  ],
});
