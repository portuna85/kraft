import { expect, test } from "@playwright/test";

/**
 * FE-SEC-02(docs/improvement.md) — 익명 방문자는 신원 조회를 하지 않는다.
 *
 * `(session)/layout.tsx`가 `kraft_logged_in` 쿠키(서버에서 읽음)로 `SessionProvider`의
 * `initialLoggedIn`을 정한다 — 쿠키가 없는 익명 방문자는 `GET /api/v1/community/session`
 * 을 아예 부르지 않아야 한다(이전엔 무조건 불렀다 — 홈 다음으로 트래픽이 많은
 * `/recommend`·`/saved`·`/community`에서 매번 불필요한 신원 조회가 나갔다).
 *
 * CSRF 쿠키는 여전히 받아야 한다 — `GET /api/v1/community/csrf`(BE-CSRF-01)로.
 * 이 스펙은 "신원 조회는 없다"와 "CSRF 쿠키는 있다"를 함께 확인해, 한쪽만 고치고
 * 다른 쪽을 깨는 회귀(1차 시도가 실제로 겪은 것)를 둘 다 잡는다.
 */
test.describe("익명 방문자 — 신원 조회 없이 CSRF 쿠키만 받는다", () => {
  test("/recommend를 열어도 /api/v1/community/session 요청이 없다", async ({ page }) => {
    const sessionRequests: string[] = [];
    const csrfRequests: string[] = [];
    page.on("request", (request) => {
      const url = request.url();
      if (url.includes("/api/v1/community/session")) sessionRequests.push(url);
      if (url.includes("/api/v1/community/csrf")) csrfRequests.push(url);
    });

    await page.goto("/recommend");
    await page.waitForFunction(() => document.cookie.includes("XSRF-TOKEN"));

    expect(sessionRequests).toHaveLength(0);
    expect(csrfRequests.length).toBeGreaterThan(0);
  });

  test("/community를 열어도 /api/v1/community/session 요청이 없다", async ({ page }) => {
    const sessionRequests: string[] = [];
    page.on("request", (request) => {
      if (request.url().includes("/api/v1/community/session")) {
        sessionRequests.push(request.url());
      }
    });

    await page.goto("/community");
    await page.waitForFunction(() => document.cookie.includes("XSRF-TOKEN"));

    expect(sessionRequests).toHaveLength(0);
  });
});
