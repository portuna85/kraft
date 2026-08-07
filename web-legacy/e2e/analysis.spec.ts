import { test, expect } from "@playwright/test";

const analysisResponse = {
  numbers: [1, 2, 3, 4, 5, 6],
  oddCount: 3,
  evenCount: 3,
  lowCount: 6,
  highCount: 0,
  sumOfNumbers: 21,
  sumBucket: "21-65",
  consecutivePairCount: 5,
  rangeDistribution: [
    { range: "1-9", count: 6 },
    { range: "10-19", count: 0 },
    { range: "20-29", count: 0 },
    { range: "30-39", count: 0 },
    { range: "40-45", count: 0 },
  ],
  wonFirstPrize: true,
  firstPrizeHistory: [
    { round: 1, drawDate: "2002-12-07", firstPrizeAmount: 0 },
  ],
};

test("유효한 번호 6개를 입력하면 분석 결과가 렌더된다", async ({ page }) => {
  await page.route("**/api/v1/stats/analysis", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(analysisResponse),
    }),
  );
  await page.goto("/analysis");

  await page.getByPlaceholder("예: 3, 11, 19, 28, 34, 42")
    .pressSequentially("1, 2, 3, 4, 5, 6");
  await page.getByRole("button", { name: "분석하기" }).click();

  await expect(page.getByRole("heading", { name: "분석 결과" })).toBeVisible();
  await expect(page.getByText("3 / 3")).toBeVisible();
  await expect(page.getByText("역대 1등 당첨 이력 1건")).toBeVisible();
  await expect(page.getByText("1회")).toBeVisible();
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
