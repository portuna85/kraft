import { expect, test } from "@playwright/test";

/**
 * 홈·조합 진단 라우트 렌더 — T-1, T-2
 *
 * 픽스처 백엔드가 상시 가동된 상태에서, 핵심 데이터를 쓰는 공개 라우트가 실제로
 * 그 데이터를 HTML에 담아 렌더하는지 확인한다. 홈은 RSC만으로 그려지므로(§8.1)
 * 최신 회차 번호가 서버 응답에 이미 포함돼야 한다.
 */
test.describe("홈·조합 진단 렌더", () => {
  test("홈이 최신 회차와 당첨금을 렌더한다", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toContainText("1150회");
    await expect(page.getByText("2026년 8월 1일 추첨")).toBeVisible();
    await expect(page.getByText("1등 당첨금")).toBeVisible();
  });

  test("홈이 scripts/deploy/smoke-test.sh가 기대하는 data-testid 구조를 유지한다", async ({
    page,
  }) => {
    // smoke-test.sh의 배포 게이트 정규식: data-testid="latest-round" 바로 다음
    // 자식이 <strong>회차 숫자다 — 문구·클래스가 바뀌어도 이 구조가 깨지면 배포가
    // 막힌다. 여기서 먼저 잡는다.
    await page.goto("/");
    const testid = page.locator('[data-testid="latest-round"]');
    await expect(testid).toBeVisible();
    const strong = testid.locator("> strong").first();
    await expect(strong).toHaveText(/^\d{3,4}회$/);
  });

  test("홈이 WebSite 구조화 데이터를 담고 있다", async ({ page }) => {
    await page.goto("/");
    const jsonLd = await page.locator('script[type="application/ld+json"]').textContent();
    expect(jsonLd).not.toBeNull();
    const data = JSON.parse(jsonLd ?? "{}") as { "@type"?: string; name?: string };
    expect(data["@type"]).toBe("WebSite");
    expect(data.name).toBe("KRAFT Lotto");
  });

  test("홈의 '내 조합 진단하기' CTA로 이동한다", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "내 조합 진단하기" }).click();
    await expect(page).toHaveURL(/\/analysis$/);
  });

  test("/analysis가 조합 진단 결과를 렌더한다", async ({ page }) => {
    await page.goto("/analysis?numbers=1,8,17,24,33,41");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });

  test("/analysis가 역대 1등 이력을 렌더한다", async ({ page }) => {
    // 픽스처는 1번이 포함된 조합을 1등 이력이 있는 것으로 응답한다(e2e/fixtures/backend.mjs).
    await page.goto("/analysis?numbers=1,8,17,24,33,41");
    await expect(page.getByText("이 조합은 과거에 1등으로 당첨된 적이 있습니다.")).toBeVisible();
    await expect(page.getByText("812회")).toBeVisible();

    await page.goto("/analysis?numbers=2,8,17,24,33,41");
    await expect(page.getByText("이 조합은 아직 1등으로 나온 적이 없습니다.")).toBeVisible();
  });

  test("모바일 하단 탭 '진단'이 /analysis로 이동한다", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");
    await page
      .getByRole("navigation", { name: "바로가기" })
      .getByRole("link", { name: "진단" })
      .click();
    await expect(page).toHaveURL(/\/analysis$/);
  });
});
