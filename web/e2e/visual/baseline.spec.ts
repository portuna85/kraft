import { expect, test } from "@playwright/test";

/**
 * 픽셀 스크린샷 회귀 베이스라인 — T-25·26
 *
 * 대표 라우트를 실콘텐츠 상태(픽스처 백엔드)로 라이트·다크 두 테마 전체 페이지
 * 스크린샷으로 고정한다. 베이스라인 갱신은
 * `npx playwright test --config=playwright.visual.config.ts --update-snapshots`이지만,
 * **로컬에서 만든 파일을 그대로 커밋하지 않는다** — CI(Linux)가 만든 actual.png를
 * 채택한다(playwright.visual.config.ts 상단 주석).
 */
const ROUTES: Array<{ name: string; path: string }> = [
  { name: "홈", path: "/" },
  { name: "번호 추천", path: "/recommend" },
  { name: "당첨 패턴", path: "/stats" },
  { name: "커뮤니티 목록", path: "/community" },
  { name: "게시글 상세", path: "/community/posts/1" },
  { name: "서비스 상태", path: "/status" },
  { name: "조합 분석", path: "/analysis" },
  { name: "번호별 빈도", path: "/frequency" },
  { name: "데이터", path: "/data" },
  { name: "보관함", path: "/saved" },
];

function fileNameOf(path: string): string {
  return path === "/" ? "home" : path.replace(/^\//, "").replace(/\//g, "_");
}

/**
 * 시계를 고정한다.
 *
 * 홈의 추첨 카운트다운은 `new Date()`로 남은 시간을 계산해 매초 갱신한다. 실제 시각을
 * 그대로 쓰면 베이스라인을 찍은 다음 초부터 스크린샷이 달라지고, 글자 수까지 바뀌어
 * ("6일 23시간 59분 59초" ↔ "5분 3초") 좁은 뷰포트에서는 줄바꿈 위치마저 흔들린다 —
 * 회귀를 잡는 대신 시계를 찍는 테스트가 된다.
 *
 * 마스킹 대신 시각을 고정하는 이유: 마스크로 덮으면 그 자리의 스타일 회귀를 영영 못
 * 잡는다. 고정 시각이면 렌더 결과가 결정적이면서 실제 화면 그대로 남는다.
 *
 * 기준 시각은 추첨(토 20:35 KST) 사이 아무 지점이면 된다 — 2026-08-12(수) 09:00 UTC.
 * `setFixedTime`은 Date만 고정하고 타이머는 건드리지 않아 하이드레이션에 영향이 없다.
 */
const FIXED_NOW = new Date("2026-08-12T09:00:00Z");

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(FIXED_NOW);
});

for (const { name, path } of ROUTES) {
  test(`${name} — 라이트 모드`, async ({ page }) => {
    await page.goto(path, { waitUntil: "networkidle" });
    await expect(page).toHaveScreenshot(`${fileNameOf(path)}-light.png`, { fullPage: true });
  });

  test(`${name} — 다크 모드`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem("kraft-theme", "dark");
    });
    await page.goto(path, { waitUntil: "networkidle" });
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page).toHaveScreenshot(`${fileNameOf(path)}-dark.png`, { fullPage: true });
  });
}
