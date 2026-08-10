import { expect, test } from "@playwright/test";

/**
 * 홈·통계 라우트 렌더 — T-1, T-2
 *
 * 픽스처 백엔드가 상시 가동된 상태에서, 핵심 데이터를 쓰는 공개 라우트가 실제로
 * 그 데이터를 HTML에 담아 렌더하는지 확인한다. 홈은 RSC만으로 그려지므로(§8.1)
 * 최신 회차 번호가 서버 응답에 이미 포함돼야 한다.
 */
test.describe("홈·통계 렌더", () => {
  test("홈이 최신 회차와 당첨금을 렌더한다", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("1150회");
    await expect(page.getByText("2026년 8월 1일 추첨")).toBeVisible();
    await expect(page.getByText("1등 당첨금")).toBeVisible();
  });

  test("번호별 출현 통계 카드로 이동한다", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "번호별 출현 통계" }).click();
    await expect(page).toHaveURL(/\/frequency$/);
  });

  test("/stats가 홀짝·고저·합계 패턴을 렌더한다", async ({ page }) => {
    await page.goto("/stats");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    // 픽스처 합계 구간 중 하나가 화면에 보여야 실제 데이터가 반영된 것이다.
    await expect(page.getByText("156-200", { exact: false })).toBeVisible();
  });

  test("/companion이 동반 출현 쌍을 렌더한다", async ({ page }) => {
    await page.goto("/companion");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });

  test("/analysis가 조합 진단 결과를 렌더한다", async ({ page }) => {
    await page.goto("/analysis?numbers=1,8,17,24,33,41");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });
});
