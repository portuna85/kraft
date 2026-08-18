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
const ROUTES = ["/", "/recommend", "/stats", "/analysis", "/frequency", "/data", "/community"];

/**
 * 홈의 추첨 카운트다운(`DrawCountdown`)은 실제 시각으로 남은 시간을 그린다 —
 * 값 길이가 "6일 23시간 59분 59초"처럼 실행 시점마다 달라져, 320~360px처럼
 * 좁은 폭에서는 우연히 실행한 순간에 따라 overflow 여부가 갈린다
 * (baseline.spec.ts가 이미 같은 이유로 시계를 고정한다 — 그 절차를 그대로
 * 따른다. 추첨 사이 아무 시각이면 된다).
 */
const FIXED_NOW = new Date("2026-08-12T09:00:00Z");

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(FIXED_NOW);
});

const BOUNDARY_WIDTHS = [320, 360, 390, 639, 640, 641, 768, 1023, 1024, 1025, 1280, 1440];

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
