import { expect, test } from "@playwright/test";

/**
 * T-21 — /ops는 KRAFT_OPS_ALLOWED_HOST 미설정 시 404다.
 *
 * 과거 이 게이트는 fail-open이었고, 그래서 /ops가 공개 도메인에서 그대로 열려 있었다
 * (레거시 F-P0-12). 이 트랙은 env를 비운 채 앱을 띄우므로, 여기서 200이 나오면 그
 * 사고가 재현된 것이다.
 */
test.describe("/ops 호스트 게이트", () => {
  test("호스트 env가 없으면 404를 반환한다", async ({ request }) => {
    const response = await request.get("/ops");
    expect(response.status()).toBe(404);
  });

  test("하위 경로도 함께 막힌다", async ({ request }) => {
    const response = await request.get("/ops/rounds");
    expect(response.status()).toBe(404);
  });

  test("404 화면에 운영 콘솔의 존재를 드러내지 않는다", async ({ page }) => {
    await page.goto("/ops");
    await expect(page.getByRole("heading", { name: "운영 콘솔" })).toHaveCount(0);
  });
});
