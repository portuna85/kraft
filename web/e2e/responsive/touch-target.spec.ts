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
    // 브랜드/로고 링크(`.brand`)는 인라인 텍스트 링크라 WCAG 2.5.8의 인라인
    // 예외 대상이다 — aria-label이 붙은 명시적 컨트롤만 검사한다.
    await page.goto("/", { waitUntil: "networkidle" });
    await assertMinHitArea(page, "header [aria-label]");
  });
});
