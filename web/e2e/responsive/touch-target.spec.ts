import { test } from "@playwright/test";

import { assertMinHitArea } from "../lib/responsive-assertions";

/**
 * 상호작용 대상의 실질 hit area는 최소 44×44px이어야 한다(`--target-min`).
 * 번호 공처럼 시각 크기는 작지만 히트 영역을 `::before`로 넓힌 요소는 이미
 * 대상에서 제외한다 — 여기서는 명시적 버튼/링크만 검사한다.
 */
test.use({ viewport: { width: 390, height: 844 } });

test.describe("44px 최소 hit area", () => {
  test("홈의 nav/footer 링크와 버튼이 44px 이상이다", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    await assertMinHitArea(page, 'nav[aria-label="바로가기"] a');
    await assertMinHitArea(page, "footer a");
  });

  test("헤더의 실제 상호작용 컨트롤(테마 토글 등)이 44px 이상이다", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    await assertMinHitArea(page, "header [aria-label]");
  });

  // KF-14(docs/improvement.md): 브랜드 링크(`.brand`)는 셸의 블록 수준 내비게이션
  // 요소라 WCAG 2.5.8의 인라인 텍스트 예외가 적용되지 않는다(§2.2② 판정) —
  // 프로젝트 자체 --target-min 계약을 따라야 한다.
  test("헤더 브랜드 링크가 44px 이상이다", async ({ page }) => {
    await page.goto("/", { waitUntil: "networkidle" });
    await assertMinHitArea(page, "header a");
  });
});
