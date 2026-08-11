import { expect, test } from "@playwright/test";

/**
 * 추천 생성 → 저장 → 보관함/이력에 반영 — T-4, T-7
 *
 * 익명 사용자(기기 토큰 스코프)의 핵심 여정이다. 픽스처 백엔드가 저장 번호·추천
 * 이력 상태를 실제로 들고 있어야(`e2e/fixtures/backend.mjs`) "생성했더니 이력에
 * 보인다"·"저장했더니 보관함에 보인다"를 확인할 수 있다.
 *
 * 테스트 간 픽스처 상태가 공유되므로(`fullyParallel: false`) 순서를 의도적으로
 * 잇는다 — 이 파일 안에서는 순서가 섞이지 않는다.
 */
test.describe("추천·저장 여정", () => {
  test("조합을 만들고 보관함에 저장한다", async ({ page }) => {
    await page.goto("/recommend");
    // 세션 조회(마운트 시 1회)가 CSRF 쿠키를 심을 때까지 기다린다 — 그 전에 쓰기
    // 요청을 보내면 transport가 스스로 막는다(§13.4).
    await page.waitForFunction(() => document.cookie.includes("XSRF-TOKEN"));

    await page.getByRole("button", { name: "조합 만들기" }).click();
    await expect(page.getByRole("heading", { name: "추천 조합" })).toBeVisible();
    // 확률 고지는 결과와 같은 화면에 있어야 한다 — 법적 요구(improvement_fe.md §25.4).
    await expect(page.getByText("추천 번호에 대해 알아두세요")).toBeVisible();

    const firstSave = page.getByRole("button", { name: "보관함에 저장" }).first();
    await firstSave.click();
    await expect(page.getByText("저장했습니다.")).toBeVisible();
  });

  test("저장한 번호가 보관함에 나타난다", async ({ page }) => {
    await page.goto("/saved");
    await expect(page.getByRole("heading", { name: "보관함" })).toBeVisible();
    // 익명 저장이 목록에 최소 1건 이상 보여야 한다 — 빈 상태 안내가 아니어야 한다.
    await expect(page.getByText("저장한 번호가 없습니다")).not.toBeVisible();
  });

  test("생성한 조합이 추천 이력에도 나타난다", async ({ page }) => {
    await page.goto("/recommend/history");
    await expect(page.getByText("아직 생성한 추천이 없습니다")).not.toBeVisible();
  });
});
