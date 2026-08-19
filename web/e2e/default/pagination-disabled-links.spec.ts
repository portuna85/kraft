import { expect, test } from "@playwright/test";

/**
 * KF-15(docs/improvement.md) — 경계 페이지네이션 링크가 키보드로 계속 활성화된다.
 *
 * `shared/ui/surface.tsx`의 `Pagination`이 처음/이전/다음/마지막을 실제 Next
 * `<Link>`로 렌더했었다. 경계에서 `aria-disabled="true"`와 CSS
 * `pointer-events:none`을 받았지만 `href`는 항상 유효한 URL이라 `tabIndex`도,
 * `onClick` 방지도 없어 탭 순서에 남고 Enter로 활성화됐다. ARIA는 상태를 전달할
 * 뿐 네이티브 링크 이동을 막지 못한다 — 경계 항목을 `PageLink`가 비링크
 * `<span>`으로 렌더하도록 고쳤다. 비링크는 애초에 포커스·Enter 활성화 대상이
 * 아니므로, 초점을 맞추고 Enter를 누르는 대신 "링크가 아니다"를 직접 단언한다.
 */
test.describe("경계 페이지네이션 링크는 키보드로 활성화되지 않는다", () => {
  test("첫 페이지에서 '처음'/'이전'은 링크가 아니라 탭 순서 밖의 텍스트다", async ({ page }) => {
    await page.goto("/community?category=WIN_STORY");

    const nav = page.getByRole("navigation", { name: "페이지 이동" });
    await expect(nav.getByRole("link", { name: "처음" })).toHaveCount(0);
    await expect(nav.getByRole("link", { name: "이전" })).toHaveCount(0);
    await expect(nav.getByText("처음", { exact: true })).toHaveAttribute("aria-disabled", "true");
    await expect(nav.getByText("이전", { exact: true })).toHaveAttribute("aria-disabled", "true");

    // 다음/마지막은 여전히 진짜 링크여야 한다 — 활성 경계까지 지워지면 안 된다.
    await expect(nav.getByRole("link", { name: "다음" })).toHaveCount(1);
    await expect(nav.getByRole("link", { name: "마지막" })).toHaveCount(1);
  });

  test("마지막 페이지에서 '다음'/'마지막'은 링크가 아니라 탭 순서 밖의 텍스트다", async ({
    page,
  }) => {
    await page.goto("/community?category=WIN_STORY&page=2");

    const nav = page.getByRole("navigation", { name: "페이지 이동" });
    await expect(nav.getByRole("link", { name: "다음" })).toHaveCount(0);
    await expect(nav.getByRole("link", { name: "마지막" })).toHaveCount(0);
    await expect(nav.getByText("다음", { exact: true })).toHaveAttribute("aria-disabled", "true");
    await expect(nav.getByText("마지막", { exact: true })).toHaveAttribute("aria-disabled", "true");

    await expect(nav.getByRole("link", { name: "처음" })).toHaveCount(1);
    await expect(nav.getByRole("link", { name: "이전" })).toHaveCount(1);
  });
});
