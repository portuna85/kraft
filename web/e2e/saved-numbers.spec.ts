import { test, expect } from "@playwright/test";

const SAVED_ITEM = {
  id: 1,
  numbers: [1, 2, 3, 4, 5, 6],
  label: "테스트 번호",
  source: "MANUAL",
  createdAt: "2026-01-01T00:00:00Z",
};

function mockSavedApi(
  page: import("@playwright/test").Page,
  options: { deleteStatus?: number; matchStatus?: number } = {},
) {
  const deleteStatus = options.deleteStatus ?? 204;
  const matchStatus = options.matchStatus ?? 200;

  page.route("**/api/v1/saved", (route) => {
    if (route.request().method() === "GET") {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([SAVED_ITEM]),
      });
    } else {
      route.continue();
    }
  });

  page.route("**/api/v1/saved/matches**", (route) => {
    if (matchStatus >= 400) {
      route.fulfill({ status: matchStatus });
    } else {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      });
    }
  });

  const deleteCalls: string[] = [];
  page.route("**/api/v1/saved/1", (route) => {
    deleteCalls.push(route.request().method());
    route.fulfill({ status: deleteStatus });
  });

  return { deleteCalls };
}

test("저장 번호 목록을 표시한다", async ({ page }) => {
  mockSavedApi(page);
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await expect(list).toBeVisible();
  await expect(list.locator(".saved-item")).toHaveCount(1);
});

// R-44: 삭제는 즉시 실행되지 않고 5초 유예("실행 취소") 후에 실제 요청이 나간다.
test("삭제 버튼 클릭 시 실행취소 상태가 되고, 5초 뒤 삭제 요청이 전송되어 행이 제거된다", async ({ page }) => {
  const { deleteCalls } = mockSavedApi(page);
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await expect(list.locator(".saved-item")).toHaveCount(1);

  await page.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }).click();
  // 유예 중에는 행이 남아 있고 실행취소 버튼으로 바뀐다 — 즉시 삭제 요청은 없다.
  await expect(list.locator(".saved-item")).toHaveCount(1);
  await expect(list.locator(".saved-item")).toHaveClass(/is-pending-delete/);
  await expect(page.getByRole("button", { name: "실행 취소" })).toBeVisible();
  expect(deleteCalls).toHaveLength(0);

  await page.waitForTimeout(5200);
  expect(deleteCalls.filter((m) => m === "DELETE")).toHaveLength(1);
  await expect(list.locator(".saved-item")).toHaveCount(0);
});

test("실행취소를 누르면 삭제 요청이 전송되지 않고 행이 유지된다", async ({ page }) => {
  const { deleteCalls } = mockSavedApi(page);
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await page.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }).click();
  await page.getByRole("button", { name: "실행 취소" }).click();

  await page.waitForTimeout(5200);
  expect(deleteCalls).toHaveLength(0);
  await expect(list.locator(".saved-item")).toHaveCount(1);
});

test("삭제 실패 시 삭제 요청이 전송됐고 행이 복구된다", async ({ page }) => {
  const { deleteCalls } = mockSavedApi(page, { deleteStatus: 500 });
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await page.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }).click();

  await page.waitForTimeout(5200);
  expect(deleteCalls.filter((m) => m === "DELETE")).toHaveLength(1);
  await expect(list.locator(".saved-item")).toHaveCount(1);
});
