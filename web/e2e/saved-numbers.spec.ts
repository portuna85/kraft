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

// FE-003: 확인 방식을 다이얼로그로 통일하면서 5초 유예("실행 취소") 삭제를 걷어냈다.
// 삭제는 확인 직후 한 번만 일어난다.
test("삭제 버튼을 눌러도 확인 전에는 삭제 요청이 나가지 않는다", async ({ page }) => {
  const { deleteCalls } = mockSavedApi(page);
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await expect(list.locator(".saved-item")).toHaveCount(1);

  await page.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }).click();

  await expect(page.getByRole("dialog")).toContainText("저장 번호를 삭제할까요?");
  await expect(list.locator(".saved-item")).toHaveCount(1);
  expect(deleteCalls).toHaveLength(0);
});

test("확인하면 삭제 요청이 전송되고 행이 제거된다", async ({ page }) => {
  const { deleteCalls } = mockSavedApi(page);
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await page.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "삭제" }).click();

  await expect(list.locator(".saved-item")).toHaveCount(0);
  expect(deleteCalls.filter((m) => m === "DELETE")).toHaveLength(1);
});

test("취소하면 삭제 요청이 전송되지 않고 행이 유지된다", async ({ page }) => {
  const { deleteCalls } = mockSavedApi(page);
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await page.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "취소" }).click();

  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(deleteCalls).toHaveLength(0);
  await expect(list.locator(".saved-item")).toHaveCount(1);
});

test("삭제 실패 시 다이얼로그 안에서 알리고 행이 유지된다", async ({ page }) => {
  const { deleteCalls } = mockSavedApi(page, { deleteStatus: 500 });
  await page.goto("/saved");

  const list = page.locator(".saved-list");
  await page.getByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "삭제" }).click();

  await expect(page.getByRole("dialog")).toContainText("저장 번호를 삭제하지 못했습니다");
  expect(deleteCalls.filter((m) => m === "DELETE")).toHaveLength(1);
  await page.getByRole("dialog").getByRole("button", { name: "취소" }).click();
  await expect(list.locator(".saved-item")).toHaveCount(1);
});
