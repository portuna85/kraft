import { test } from "@playwright/test";

import { assertNoNavigationOnKeyboardActivation } from "../lib/interaction-assertions";

/**
 * KF-15(docs/improvement.md) — 경계 페이지네이션 링크가 키보드로 계속 활성화된다.
 *
 * `shared/ui/surface.tsx`의 `Pagination`이 처음/이전/다음/마지막을 실제 Next
 * `<Link>`로 렌더한다. 경계에서 `aria-disabled="true"`와 CSS `pointer-events:none`을
 * 받지만 `href`는 항상 유효한 URL이라 `tabIndex`도, `onClick` 방지도 없어 탭 순서에
 * 남고 Enter로 활성화된다. ARIA는 상태를 전달할 뿐 네이티브 링크 이동을 막지 못한다.
 *
 * **이 테스트는 지금 실패해야 정상이다(red).**
 */
test.describe("경계 페이지네이션 링크는 키보드로 활성화되지 않는다", () => {
  test("첫 페이지에서 '처음'/'이전' 링크가 Enter로 이동하지 않는다", async ({ page }) => {
    test.fail(true, "KF-15: 비활성 링크가 Enter로 여전히 이동함 — 근본 수정 전까지 알려진 실패");
    await page.goto("/community?category=WIN_STORY");

    const nav = page.getByRole("navigation", { name: "페이지 이동" });
    await assertNoNavigationOnKeyboardActivation(page, nav.getByRole("link", { name: "처음" }));
    await assertNoNavigationOnKeyboardActivation(page, nav.getByRole("link", { name: "이전" }));
  });

  test("마지막 페이지에서 '다음'/'마지막' 링크가 Enter로 이동하지 않는다", async ({ page }) => {
    test.fail(true, "KF-15: 비활성 링크가 Enter로 여전히 이동함 — 근본 수정 전까지 알려진 실패");
    await page.goto("/community?category=WIN_STORY&page=2");

    const nav = page.getByRole("navigation", { name: "페이지 이동" });
    await assertNoNavigationOnKeyboardActivation(page, nav.getByRole("link", { name: "다음" }));
    await assertNoNavigationOnKeyboardActivation(page, nav.getByRole("link", { name: "마지막" }));
  });
});
