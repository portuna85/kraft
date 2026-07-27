import { test, expect } from "@playwright/test";

// /analysis는 순수 클라이언트 계산(백엔드 미의존)이라 실제 입력→결과 흐름을 검증할 수 있다.

test("유효한 번호 6개를 입력하면 분석 결과가 렌더된다", async ({ page }) => {
  await page.goto("/analysis");

  await page.getByPlaceholder("예: 3, 11, 19, 28, 34, 42")
    .pressSequentially("1, 2, 3, 4, 5, 6");
  await page.getByRole("button", { name: "분석하기" }).click();

  await expect(page.getByRole("heading", { name: "분석 결과" })).toBeVisible();
  await expect(page.getByText("3 / 3")).toBeVisible();
});

test("번호가 6개가 아니면 에러 메시지를 보여준다", async ({ page }) => {
  await page.goto("/analysis");

  await page.getByPlaceholder("예: 3, 11, 19, 28, 34, 42")
    .pressSequentially("1, 2, 3");
  await page.getByRole("button", { name: "분석하기" }).click();

  await expect(page.getByText("번호는 6개여야 합니다.")).toBeVisible();
});

test("중복된 번호를 입력하면 에러 메시지를 보여준다", async ({ page }) => {
  await page.goto("/analysis");

  await page.getByPlaceholder("예: 3, 11, 19, 28, 34, 42")
    .pressSequentially("1, 1, 3, 4, 5, 6");
  await page.getByRole("button", { name: "분석하기" }).click();

  await expect(page.getByText("번호에 중복이 있습니다.")).toBeVisible();
});
