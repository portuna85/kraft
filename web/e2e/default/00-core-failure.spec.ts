import { expect, test } from "@playwright/test";

/**
 * 핵심 데이터 실패 → 5xx — T-20, improvement_fe.md §6.5
 *
 * 홈은 최신 회차 조회 실패를 페이지 안에서 흡수하지 않는다 — 그대로 던져 (public)
 * 셸의 error 경계가 5xx로 받게 둔다. 200 폴백으로 조용히 덮으면 업타임 체커와
 * 크롤러가 실제 장애를 놓친다(홈 라우트 주석 참고).
 *
 * `/__test__/fail`은 픽스처 백엔드(`e2e/fixtures/backend.mjs`)가 제공하는 테스트
 * 전용 제어 경로다 — 지정한 경로의 다음 응답을 503으로 강제한다.
 *
 * **파일 이름의 `00-` 접두사는 장식이 아니다.** `getLatestRound()`는 revalidate
 * 캐시를 쓰고(§13.6), 한 번이라도 성공하면 이후 요청은 백엔드가 죽어도 그 캐시를
 * 계속 서빙한다(stale-while-error) — `revalidateTag`를 호출해도 마찬가지다(백그라운드
 * 재검증만 트리거할 뿐 그 자리에서 강제로 새로 받아오지 않는다). 그래서 이 트랙에서
 * "/"를 처음 방문하는 테스트가 반드시 이 파일이어야 한다. `playwright.default.config.ts`의
 * 준비 상태 확인도 일부러 `/robots.txt`를 쓴다 — `/`를 쓰면 Playwright 자신의 폴링이
 * 이 캐시를 데워 버린다. 다른 spec 파일을 추가할 때 이 파일보다 알파벳순으로 앞서게
 * 하거나, 이 파일보다 먼저 "/"를 방문하게 하지 않는다.
 */
test.describe("핵심 데이터 실패 전파", () => {
  test.afterEach(async ({ request }) => {
    await request.post("http://127.0.0.1:4111/__test__/reset");
  });

  test("최신 회차 조회가 실패하면 홈이 200이 아니다", async ({ page, request }) => {
    await request.put("http://127.0.0.1:4111/__test__/fail?path=/api/v1/rounds/latest");

    const response = await page.goto("/");
    expect(response?.status()).toBeGreaterThanOrEqual(500);
  });

  test("장애 해제 후에는 다시 정상 렌더된다", async ({ page, request }) => {
    await request.put("http://127.0.0.1:4111/__test__/fail?path=/api/v1/rounds/latest");
    await page.goto("/").catch(() => undefined);

    await request.post("http://127.0.0.1:4111/__test__/reset");
    const response = await page.goto("/");
    expect(response?.status()).toBe(200);
  });
});
