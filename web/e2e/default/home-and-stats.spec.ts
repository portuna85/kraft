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

  test("홈의 인사이트 미리보기 카드로 이동한다", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "번호별 출현", exact: false }).click();
    await expect(page).toHaveURL(/\/frequency$/);
  });

  test("홈의 '내 조합 진단하기' CTA로 이동한다", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "내 조합 진단하기" }).click();
    await expect(page).toHaveURL(/\/analysis$/);
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

  test("/companion은 처음에 12쌍만 그리고, 더 보기를 눌러도 50쌍을 넘지 않는다", async ({
    page,
  }) => {
    await page.goto("/companion");

    const rows = page.getByRole("table").getByRole("row");
    // 헤더 행 1개 + 본문 12개.
    await expect(rows).toHaveCount(13);

    await page.getByRole("button", { name: "상위 50개 모두 보기" }).click();

    // §23.5 불변식(990쌍 전량 전송 금지, 초기 페이로드 상위 50쌍)이 살아있는 한
    // "더 보기"로 펼쳐도 50을 넘을 수 없다 — 이 값 자체가 서버가 보낸 상한이다.
    await expect(rows).toHaveCount(51);
    await expect(page.getByRole("button", { name: /모두 보기/ })).not.toBeVisible();
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

  test("/frequency가 최다·최소 출현 6개를 렌더하고 기간 필터가 동작한다", async ({ page }) => {
    await page.goto("/frequency");

    const topSection = page.getByRole("heading", { name: "가장 많이 나온 6개" }).locator("..");
    await expect(topSection.getByRole("listitem")).toHaveCount(6);

    const bottomSection = page.getByRole("heading", { name: "가장 적게 나온 6개" }).locator("..");
    await expect(bottomSection.getByRole("listitem")).toHaveCount(6);

    await expect(page.getByRole("link", { name: "전체", exact: true })).toHaveAttribute(
      "aria-current",
      "page",
    );

    await page.getByRole("link", { name: "최근 100회" }).click();
    await expect(page).toHaveURL(/\/frequency\?limit=100$/);
    await expect(page.getByRole("link", { name: "최근 100회" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  test("/data 허브가 4개 기능 카드를 렌더하고 각 카드가 해당 라우트로 이동한다", async ({
    page,
  }) => {
    await page.goto("/data");
    await expect(page.getByRole("heading", { name: "데이터와 분석" })).toBeVisible();

    const cards: Array<[string, RegExp]> = [
      ["출현 통계", /\/frequency$/],
      ["패턴 통계", /\/stats$/],
      ["동반 출현", /\/companion$/],
      ["번호 분석", /\/analysis$/],
    ];
    for (const [title] of cards) {
      await expect(page.getByRole("heading", { name: title })).toBeVisible();
    }

    await page
      .getByRole("listitem")
      .filter({ hasText: "패턴 통계" })
      .getByRole("link", { name: "보기" })
      .click();
    await expect(page).toHaveURL(/\/stats$/);
  });

  test("모바일 하단 탭 '데이터'가 /data로 이동한다", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/");
    await page
      .getByRole("navigation", { name: "바로가기" })
      .getByRole("link", { name: "데이터" })
      .click();
    await expect(page).toHaveURL(/\/data$/);
  });
});
