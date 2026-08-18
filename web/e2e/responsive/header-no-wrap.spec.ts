import { test } from "@playwright/test";

import { assertElementMaxHeight } from "../lib/responsive-assertions";

/**
 * KF-02(docs/improvement.md) — 데스크톱 헤더가 1024~1151px에서 깨진다.
 *
 * 브레이크포인트는 640px/1024px뿐이다(`shared/config/breakpoints.ts`의
 * `BP.desktop=1024`). `.primaryNav`(shell.module.css)는 정확히 `>=1024px`에서
 * 나타나 `nav-items.ts`의 `PRIMARY_NAV` 8개 항목과 함께 `.brand`·`.headerActions`와
 * 좁은 공간을 다툰다. `.brand`에는 `white-space:nowrap`/`flex-shrink:0`이 없어
 * 2줄로 래핑될 수 있다 — 감사 실측 기준선은 1023px/≥1152px에서 브랜드 높이 ~29px
 * (1줄), 1024~1151px에서 ~57px(2줄)이다.
 *
 * **1024/1060/1100/1151px 케이스는 지금 실패해야 정상이다(red).** 1152/1280px는
 * 이미 정상이어야 하며, 이 두 폭이 green으로 남는 것 자체가 결함 구간이
 * 1024~1151px로 정확히 국한됨을 증명한다.
 */
const WIDTHS = [1024, 1060, 1100, 1151, 1152, 1280];

test.describe("데스크톱 헤더 브랜드가 좁은 폭에서 래핑되지 않는다", () => {
  for (const width of WIDTHS) {
    test(`${width}px에서 브랜드 링크 높이가 32px 이하다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/", { waitUntil: "networkidle" });
      await assertElementMaxHeight(page, "header a", 32);
    });
  }
});
