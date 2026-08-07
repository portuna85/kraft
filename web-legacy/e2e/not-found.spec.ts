import { test, expect } from "@playwright/test";

test("존재하지 않는 경로는 404 페이지를 보여주고 홈으로 돌아갈 수 있다", async ({ page }) => {
  await page.goto("/this-page-does-not-exist");

  await expect(page.getByText("페이지를")).toBeVisible();
  await expect(page.getByText("찾을 수 없습니다")).toBeVisible();

  await page.getByRole("link", { name: "홈으로 이동" }).click();
  await expect(page).toHaveURL("/");
});
