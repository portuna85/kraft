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
 * **실측(이 스펙으로 재현): 1024/1060/1100px는 지금 실패해야 정상이다(red).**
 * 1151/1152/1280px는 실측상 이미 정상이다 — 감사 문서가 1151px까지를 결함
 * 구간으로 봤지만, 재현해 보면 1151px에서는 이미 브랜드가 1줄로 돌아온다. 이
 * 스펙의 실측치를 근거로 삼는다.
 */
const WIDTHS = [1024, 1060, 1100, 1151, 1152, 1280];

// KF-02가 아직 안 고쳐진 폭 — 근본 수정(§10 4단계) 전까지 알려진 실패로 남긴다.
const KNOWN_WRAP_WIDTHS = new Set([1024, 1060, 1100]);

test.describe("데스크톱 헤더 브랜드가 좁은 폭에서 래핑되지 않는다", () => {
  for (const width of WIDTHS) {
    test(`${width}px에서 브랜드 링크 높이가 32px 이하다`, async ({ page }) => {
      test.fail(
        KNOWN_WRAP_WIDTHS.has(width),
        "KF-02: 1024~1100px에서 브랜드가 2줄로 래핑됨 — 근본 수정 전까지 알려진 실패",
      );
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/", { waitUntil: "networkidle" });
      await assertElementMaxHeight(page, "header a", 32);
    });
  }
});
