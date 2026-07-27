import { test, expect, type Locator, type Page } from "@playwright/test";

// Phase 1 2단계: 모바일/태블릿 폭에서는 ThemeToggle이 MobileSecondaryMenu 드로어 안으로
// 옮겨갔다(데스크톱용 ThemeToggle은 CSS로만 숨겨진 채 DOM에 남아 있어 범위를 좁히지 않으면
// 2개가 매치된다). 햄버거("메뉴 열기")가 보이면 드로어를 열고 그 안에서, 아니면(데스크톱)
// 페이지에서 직접 버튼을 찾는다.
async function resolveThemeToggleRoot(page: Page): Promise<Page | Locator> {
  const hamburger = page.getByRole("button", { name: "메뉴 열기" });
  if (await hamburger.isVisible()) {
    await hamburger.click();
    return page.getByRole("dialog");
  }
  return page;
}

test("테마 토글을 누르면 다크 모드로 전환되고 새로고침 후에도 유지된다", async ({ page }) => {
  const hydrationErrors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error" && message.text().includes("hydrated")) {
      hydrationErrors.push(message.text());
    }
  });

  await page.goto("/");

  const root = await resolveThemeToggleRoot(page);
  await expect(page.locator("html")).not.toHaveAttribute("data-theme", "dark");

  await root.getByRole("button", { name: "다크 모드로 전환" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(root.getByRole("button", { name: "라이트 모드로 전환" })).toBeVisible();

  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  expect(hydrationErrors).toEqual([]);
});

test("다크 모드에서 다시 누르면 라이트 모드로 돌아간다", async ({ page }) => {
  await page.goto("/");

  const root = await resolveThemeToggleRoot(page);
  await root.getByRole("button", { name: "다크 모드로 전환" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");

  await root.getByRole("button", { name: "라이트 모드로 전환" }).click();
  await expect(page.locator("html")).not.toHaveAttribute("data-theme", "dark");
});
