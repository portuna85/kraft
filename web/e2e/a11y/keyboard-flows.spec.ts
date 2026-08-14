import { expect, test } from "@playwright/test";

/**
 * 키보드만으로 핵심 흐름 완주 — T-24
 *
 * axe는 정적 위반만 잡는다 — 실제로 마우스 없이 버튼까지 도달해 누를 수 있는지는
 * 별도로 확인해야 한다. 마우스 클릭을 전혀 쓰지 않고 Tab·Enter·Space만으로 조작한다.
 */
test.describe("키보드만으로 조작", () => {
  test("Tab으로 조합 만들기 버튼까지 도달해 Enter로 실행한다", async ({ page }) => {
    await page.goto("/recommend");
    await page.waitForFunction(() => document.cookie.includes("XSRF-TOKEN"));

    const generateButton = page.getByRole("button", { name: "조합 만들기" });
    await generateButton.focus();
    await expect(generateButton).toBeFocused();
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: "추천 조합" })).toBeVisible();
  });

  test("Tab으로 신고 다이얼로그를 열고 Escape로 닫는다", async ({ page }) => {
    await page.goto("/community/posts/1");

    const reportButton = page.getByRole("button", { name: "이 글 신고" });
    await reportButton.focus();
    await page.keyboard.press("Enter");

    const dialog = page.getByRole("dialog", { name: "신고 사유 선택" });
    await expect(dialog).toBeVisible();

    await page.keyboard.press("Escape");
    await expect(dialog).not.toBeVisible();
  });

  test("세그먼트 모드를 화살표로 바꾸고 번호를 Enter로 고정·제외한다", async ({ page }) => {
    await page.goto("/recommend");

    const lockedMode = page.getByRole("radio", { name: "고정 번호" });
    // 접근 이름("1번" → "1번 고정됨")이 상태에 따라 바뀌므로, role/name이 아니라
    // 안정적인 data-number 속성으로 같은 번호 버튼을 계속 가리킨다.
    const firstBall = page.locator('[data-number="1"]');

    // 기본 모드(고정)에서 1번을 고정한다.
    await firstBall.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByText("고정 1/5 · 제외 0")).toBeVisible();

    // 화살표로 제외 모드로 바꾸고 같은 번호를 누르면 고정에서 빠져 제외로 옮겨간다.
    await lockedMode.focus();
    await page.keyboard.press("ArrowRight");
    await expect(page.getByRole("radio", { name: "제외 번호" })).toHaveAttribute(
      "aria-checked",
      "true",
    );

    await firstBall.focus();
    await page.keyboard.press("Enter");
    await expect(page.getByText("고정 0/5 · 제외 1")).toBeVisible();
  });
});
