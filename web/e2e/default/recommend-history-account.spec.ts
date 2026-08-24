import { expect, test } from "@playwright/test";

import { fixtureBackendUrl } from "../lib/fixture-backend";

/**
 * KF-01(docs/improvement.md) — 로그인 상태의 추천 생성이 계정 이력에 반영되지 않는다.
 *
 * 이전에는 `recommendNumbers()`가 로그인 여부와 무관하게 항상 `deviceScoped: true`로
 * `/api/v1/numbers/recommend`를 호출했고, 백엔드에 계정 스코프로 저장하는 경로가
 * 아예 없었다. §10 3단계에서 고쳤다 — 로그인 상태면 `use-recommend-studio.ts`의
 * `generate()`가 `recommendNumbersForAccount()`로 분기해
 * `/api/v1/community/me/recommendation-sets`(POST, 인증 필요)를 호출하고, 백엔드
 * (`MyLibraryController.recommend` → `LottoRecommendationService.recommendForOwner`)가
 * `CommunityPrincipal`로 확정한 계정 id로 직접 저장한다.
 *
 * 픽스처(`e2e/fixtures/domains/community.mjs`)의
 * `/api/v1/community/me/recommendation-sets` POST 핸들러가 이 쓰기 경로를 흉내낸다.
 */
test.describe("로그인 상태의 추천 생성이 계정 이력에 나타난다", () => {
  test.beforeEach(async ({ request, context, baseURL }) => {
    await request.put(
      `${fixtureBackendUrl(baseURL)}/__test__/session?loggedIn=true&userId=1&nickname=%ED%85%8C%EC%8A%A4%ED%84%B0`,
    );
    // FE-SEC-02(docs/improvement.md): `(session)/layout.tsx`가 이제 실제 브라우저
    // 쿠키(`kraft_logged_in`)로 신원 조회 여부를 가른다 — 위 `__test__/session`은
    // 픽스처 프로세스 전역 상태만 바꿀 뿐 `page`의 쿠키 저장소와 별개라, 이 쿠키가
    // 없으면 프론트가 이 방문자를 익명으로 보고 신원 조회 자체를 건너뛴다.
    await context.addCookies([
      { name: "kraft_logged_in", value: "1", url: baseURL ?? "http://127.0.0.1:3111" },
    ]);
  });

  test.afterEach(async ({ request, baseURL }) => {
    await request.post(`${fixtureBackendUrl(baseURL)}/__test__/reset`);
  });

  test("로그인 상태에서 생성한 조합이 계정 추천 이력에 즉시 나타난다", async ({ page }) => {
    await page.goto("/recommend");
    await page.waitForFunction(() => document.cookie.includes("XSRF-TOKEN"));

    await page.getByRole("button", { name: "조합 만들기" }).click();
    await expect(page.getByRole("heading", { name: "추천 조합" })).toBeVisible();

    await page.goto("/recommend/history");
    // `expect(locator).not.toBeVisible()`는 목표 텍스트가 "아직 없음"도 "안 보임"으로
    // 즉시 통과시킨다 — 계정 이력 응답이 오기 전(로딩 문구만 떠 있는 순간)에 걸리면
    // 검증이 실제로 아무것도 확인하지 않은 채 항상 초록이 된다. 계정 스코프 응답이
    // 실제로 도착한 뒤에만 판단하도록 명시적으로 기다린다.
    await page.waitForResponse(
      (response) =>
        response.url().includes("/api/v1/community/me/recommendation-sets") &&
        response.status() === 200,
    );
    await expect(page.getByText("아직 생성한 추천이 없습니다")).not.toBeVisible();
  });
});
