import { test } from "@playwright/test";

import { assertFormControlFontSizeAtLeast16px } from "../lib/responsive-assertions";

/**
 * F-01 회귀 고정 — iOS Safari는 16px 미만 폼 컨트롤에 포커스가 들어가면 페이지를
 * 자동 확대한다. `c18c447`에서 `.control` 계열의 font-size를 16px 이상으로
 * 고쳤으므로, 여기서는 그 계약이 다시 깨지지 않는지 모바일 뷰포트에서 단언한다.
 */
test.use({ viewport: { width: 390, height: 844 } });

test.describe("폼 컨트롤 font-size 16px 이상", () => {
  test("커뮤니티 검색 입력이 16px 이상이다", async ({ page }) => {
    await page.goto("/community", { waitUntil: "networkidle" });
    await assertFormControlFontSizeAtLeast16px(page);
  });

  test("추천 스튜디오 입력이 16px 이상이다", async ({ page }) => {
    await page.goto("/recommend", { waitUntil: "networkidle" });
    await assertFormControlFontSizeAtLeast16px(page);
  });

  test("보관함 회차 대조 입력이 16px 이상이다", async ({ page }) => {
    await page.goto("/saved", { waitUntil: "networkidle" });
    await assertFormControlFontSizeAtLeast16px(page);
  });
});
