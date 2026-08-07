import { test, expect } from "@playwright/test";

test("추천 생성 후 저장하면 저장됨으로 표시된다", async ({ page }) => {
  await page.route("**/api/v1/numbers/recommend", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        recommendations: [
          [1, 2, 3, 4, 5, 6],
          [7, 8, 9, 10, 11, 12],
          [13, 14, 15, 16, 17, 18],
          [19, 20, 21, 22, 23, 24],
          [25, 26, 27, 28, 29, 30],
        ],
      }),
    });
  });
  await page.route("**/api/v1/saved", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({ created: true }),
      });
      return;
    }
    await route.continue();
  });

  await page.goto("/recommend");

  // F-P0-6/7: 홈/추천 페이지가 더 이상 마운트 시 자동으로 추천을 요청하지 않는다 —
  // 사용자가 명시적으로 생성 버튼을 눌러야 한다.
  await page.getByRole("button", { name: "추천 생성" }).click();

  const firstCard = page.locator(".recommend-card").first();
  await expect(firstCard).toBeVisible();

  await firstCard.getByRole("button", { name: "추천 1 조합 저장" }).click();
  await expect(firstCard.getByRole("button", { name: "추천 1 조합 저장됨" })).toBeVisible();
});

// FE-P0-3 재발 방지: 카드는 생성되지만 번호 6개가 그려지지 않는 회귀(§7.3.2)를 잡는다 —
// DOM에 카드/저장 버튼이 있다는 것만으로는 번호가 실제로 보인다는 보장이 되지 않는다.
test("추천 결과 카드마다 번호 6개가 실제로 보인다", async ({ page }) => {
  await page.route("**/api/v1/numbers/recommend", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        recommendations: [
          [1, 2, 3, 4, 5, 6],
          [7, 8, 9, 10, 11, 12],
          [13, 14, 15, 16, 17, 18],
          [19, 20, 21, 22, 23, 24],
          [25, 26, 27, 28, 29, 30],
        ],
      }),
    });
  });

  await page.goto("/recommend");
  await page.getByRole("button", { name: "추천 생성" }).click();

  const cards = page.locator(".recommend-card");
  await expect(cards).toHaveCount(5);

  for (const card of await cards.all()) {
    const balls = card.locator(".ball:not(.bonus-ball)");
    await expect(balls).toHaveCount(6);

    for (const ball of await balls.all()) {
      await expect(ball).toBeVisible();
      await expect(ball).not.toHaveText("");

      const { width, color, backgroundColor } = await ball.evaluate((el) => {
        const style = getComputedStyle(el);
        return {
          width: el.getBoundingClientRect().width,
          color: style.color,
          backgroundColor: style.backgroundColor,
        };
      });
      expect(width).toBeGreaterThan(0);
      expect(color).not.toBe(backgroundColor);
    }
  }
});
