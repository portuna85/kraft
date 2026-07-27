import { test, expect } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// Phase 0 기준선(docs/improvement.md §17 Phase 0): Phase 1~6에서 디자인 토큰/셸/컴포넌트를
// 옮기다 시각적으로 뭔가 깨지면 여기서 잡는다. 라우트·테마 조합마다 전체 페이지
// 스크린샷을 고정한다 — 베이스라인 갱신은 `npx playwright test --config=playwright.visual.config.ts
// --update-snapshots`.
const ROUTES: readonly { path: string; label: string; readySelector: string }[] = [
  { path: "/", label: "홈", readySelector: ".result-panel .balls" },
  { path: "/frequency", label: "출현 통계", readySelector: ".freq-summary" },
  { path: "/community", label: "커뮤니티", readySelector: "main" },
  { path: "/recommend", label: "번호 추천", readySelector: "main" },
  { path: "/saved", label: "저장 번호", readySelector: "main" },
];

const THEMES = ["light", "dark"] as const;

for (const theme of THEMES) {
  test.describe(`${theme} 테마`, () => {
    test.beforeEach(async ({ page }) => {
      if (theme === "dark") {
        await page.addInitScript(() => {
          localStorage.setItem("kraft-theme", "dark");
        });
      }
    });

    for (const route of ROUTES) {
      test(`${route.label} (${route.path})`, async ({ page }) => {
        await gotoAndWaitForRealContent(page, route.path);
        await expect(page.locator(route.readySelector).first()).toBeVisible();
        await expect(page).toHaveScreenshot(`${route.label}-${theme}.png`, { fullPage: true });
      });
    }
  });
}
