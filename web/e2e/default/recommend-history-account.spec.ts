import { expect, test } from "@playwright/test";

/**
 * KF-01(docs/improvement.md) — 로그인 상태의 추천 생성이 계정 이력에 반영되지 않는다.
 *
 * `entities/recommendation/api.ts`의 `recommendNumbers()`는 로그인 여부와 무관하게
 * 항상 `deviceScoped: true`로 `/api/v1/numbers/recommend`를 호출하고, 백엔드
 * (`RecommendApiController`/`LottoRecommendationService`)는 device-token 해시로만
 * 영속화한다 — 계정 스코프로 저장하는 경로가 아예 없다. 그런데 로그인 사용자의 이력
 * 화면(`recommend-history-list.tsx`)은 `/api/v1/community/me/recommendation-sets`
 * (계정 스코프)만 조회한다. 그 결과 로그인 상태에서 생성한 조합이 이력에서 사라진
 * 것처럼 보인다.
 *
 * **이 테스트는 지금 실패해야 정상이다(red).** KF-01을 수정하면(예: 로그인 사용자의
 * 생성이 계정 스코프로 저장되도록 프론트·백엔드를 함께 바꾸면) 통과해야 한다.
 *
 * 픽스처(`e2e/fixtures/domains/community.mjs`)는 이 결함을 그대로 재현하도록
 * 설계했다 — `/api/v1/numbers/recommend`는 여전히 device-scoped 배열에만 쓰고,
 * `/api/v1/community/me/recommendation-sets`는 항상 빈 목록을 반환한다. 이 fixture의
 * 쓰기 경로를 "고쳐서" 테스트를 통과시키지 말 것 — 그러면 결함이 아니라 픽스처를
 * 고치는 셈이다.
 */
test.describe("로그인 상태의 추천 생성이 계정 이력에 나타난다", () => {
  test.beforeEach(async ({ request }) => {
    await request.put(
      "http://127.0.0.1:4111/__test__/session?loggedIn=true&userId=1&nickname=%ED%85%8C%EC%8A%A4%ED%84%B0",
    );
  });

  test.afterEach(async ({ request }) => {
    await request.post("http://127.0.0.1:4111/__test__/reset");
  });

  test("로그인 상태에서 생성한 조합이 계정 추천 이력에 즉시 나타난다", async ({ page }) => {
    await page.goto("/recommend");
    await page.waitForFunction(() => document.cookie.includes("XSRF-TOKEN"));

    await page.getByRole("button", { name: "조합 만들기" }).click();
    await expect(page.getByRole("heading", { name: "추천 조합" })).toBeVisible();

    await page.goto("/recommend/history");
    await expect(page.getByText("아직 생성한 추천이 없습니다")).not.toBeVisible();
  });
});
