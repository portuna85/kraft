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
