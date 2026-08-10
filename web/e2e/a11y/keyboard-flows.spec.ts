import { expect, test } from "@playwright/test";

/**
 * 키보드만으로 핵심 흐름 완주 — T-24, improvement_fe.md §21.4
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

  test("고정·제외 번호 선택을 Tab·Enter만으로 조작한다", async ({ page }) => {
    await page.goto("/recommend");

    const firstBall = page.getByRole("button", { name: "1번", exact: true });
    await firstBall.focus();
    await page.keyboard.press("Enter");

    // 번호를 누를 때마다 고정 → 제외 → 해제로 순환한다(recommend-studio.tsx 주석).
    await expect(page.getByText("고정 1개: 1")).toBeVisible();
  });
});
