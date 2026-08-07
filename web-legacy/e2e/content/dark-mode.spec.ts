import { test, expect } from "@playwright/test";
import { expectNoOverflow } from "../lib/expect-no-overflow";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// 다크 모드에서도 반응형 레이아웃이 깨지지 않는지 대표 화면 1~2개로 재검사한다
// (문서: "전 라우트에 다 걸 필요는 없고 대표 화면 1~2개면 충분하다"). 색상만 바뀌는 게
// 보통이지만, 스크롤 어포던스 마스크(mask-image)나 테두리·그림자 두께 차이가 실제로
// box 크기에 영향을 줄 가능성을 배제하기 위해 실콘텐츠 상태에서 직접 확인한다.
// THEME_INIT_SCRIPT가 localStorage.kraft-theme을 읽어 <html data-theme="dark">를
// 초기 렌더 전에 붙이므로, addInitScript로 첫 스크립트 실행 전에 값을 심어둔다.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("kraft-theme", "dark");
  });
});

for (const width of [320, 1280] as const) {
  test(`다크 모드 홈 — ${width}px, 오버플로 없음`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await gotoAndWaitForRealContent(page, "/");

    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page.locator(".result-panel .balls")).toBeVisible();
    await expectNoOverflow(page);
  });

  test(`다크 모드 /frequency — ${width}px, 오버플로 없음`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await gotoAndWaitForRealContent(page, "/frequency");

    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page.locator(".freq-summary")).toBeVisible();
    await expectNoOverflow(page);
  });

  test(`다크 모드 /community/posts/1 — ${width}px, 오버플로 없음`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.route("**/api/v1/community/posts/1/comments*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ topLevel: [], totalTopLevelComments: 0, page: 0, totalPages: 0 }),
      })
    );
    await gotoAndWaitForRealContent(page, "/community/posts/1");

    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page.getByRole("heading", { name: "테스트 게시글" })).toBeVisible();
    await expectNoOverflow(page);
  });
}
