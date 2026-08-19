import { test } from "@playwright/test";

import { assertNoHorizontalOverflow } from "../lib/responsive-assertions";

/**
 * 경계 뷰포트·400% 확대 상당 폭에서 document 자체가 가로로 스크롤되지
 * 않아야 한다 — WCAG 2.1 SC 1.4.10(Reflow), Phase 1
 * (docs/improvement_codex_fe.md §8.1 PR boundary / §8.2 FE-RSP-07).
 *
 * 표의 `.tableWrap` 같은 의도적 내부 scroller는 여기서 확인하지 않는다 —
 * document 레벨 scrollWidth만 본다.
 */
const ROUTES = [
  "/",
  "/recommend",
  "/recommend/history",
  "/stats",
  "/analysis",
  "/frequency",
  "/data",
  "/community",
  "/community/write",
  "/community/posts/1",
  "/community/posts/1/edit",
  "/companion",
  "/status",
  "/saved",
  "/ops",
  "/info/data-source",
];

const BOUNDARY_WIDTHS = [320, 360, 390, 639, 640, 641, 768, 1023, 1024, 1025, 1280, 1440];

// KF-03(docs/improvement.md): Firefox에서만 /stats@320/640/641px가 실제로 가로
// 오버플로됐었다(CSS Grid automatic minimum size 차이). §10 4단계에서
// surface.module.css의 .card에 min-width:0을 추가해 고쳤다 — firefox-overflow
// 프로젝트도 이제 나머지 엔진과 동일하게 전 구간 통과해야 한다.

test.describe("경계 뷰포트에서 document 가로 스크롤 없음", () => {
  for (const width of BOUNDARY_WIDTHS) {
    test(`${width}px에서 모든 라우트가 가로 스크롤 없이 렌더된다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      for (const path of ROUTES) {
        await page.goto(path, { waitUntil: "networkidle" });
        await assertNoHorizontalOverflow(page);
      }
    });
  }
});

test.describe("400% 확대 상당(320×512)에서 가로 스크롤 없음", () => {
  test("모든 라우트가 320×512에서 가로 스크롤 없이 렌더된다", async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 512 });
    for (const path of ROUTES) {
      await page.goto(path, { waitUntil: "networkidle" });
      await assertNoHorizontalOverflow(page);
    }
  });
});

test.describe("가로모드(844×390)에서 가로 스크롤 없음", () => {
  test("주요 라우트가 가로모드에서 가로 스크롤 없이 렌더된다", async ({ page }) => {
    await page.setViewportSize({ width: 844, height: 390 });
    for (const path of ["/", "/recommend", "/stats"]) {
      await page.goto(path, { waitUntil: "networkidle" });
      await assertNoHorizontalOverflow(page);
    }
  });
});
