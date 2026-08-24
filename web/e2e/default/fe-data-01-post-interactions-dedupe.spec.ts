import { expect, test } from "@playwright/test";

/**
 * FE-DATA-01(docs/improvement.md) — 게시글 상세 개인 상호작용 조회 3회 → 1회.
 *
 * 로그인 상태로 게시글 상세를 열면 `ReactionBar`(좋아요/북마크)·`BlockedPostGate`·
 * `BlockButton`(둘 다 차단 여부)이 각자 `getMyInteractions`/`getBlockedUsers`를 따로
 * 불렀다 — 셋 다 `use-post-interactions.ts`의 공유 `me:interactions:${postId}` 리소스
 * 키로 옮긴 뒤에는 실제 네트워크 요청이 1회로 합쳐진다.
 *
 * 로그인은 `e2e-nickname` 쿠키로 흉내낸다(RSP-25, `community.mjs`의
 * `sessionFromCookie`) — 픽스처 전역 상태를 건드리지 않고 이 브라우저 컨텍스트에서만
 * 로그인 상태로 응답하게 한다. 고정 픽스처 게시글(id=1, ownerId=10)은 로그인 사용자
 * (userId=1)와 다른 소유자라 `BlockButton`이 실제로 렌더된다.
 *
 * FE-SEC-02(docs/improvement.md): `kraft_logged_in`도 함께 심는다 —
 * `(session)/layout.tsx`가 이 쿠키로 신원 조회(`fetchSession`) 호출 여부를 가르므로,
 * 없으면 `e2e-nickname`을 읽는 `sessionFromCookie` 자체가 호출되지 않는다.
 */
test.describe("게시글 상세 개인 상호작용 조회 dedupe", () => {
  test.beforeEach(async ({ context }) => {
    await context.addCookies([
      {
        name: "e2e-nickname",
        value: encodeURIComponent("테스트유저"),
        url: "http://127.0.0.1:3111",
      },
      { name: "kraft_logged_in", value: "1", url: "http://127.0.0.1:3111" },
    ]);
  });

  test("좋아요/북마크/차단 조회가 합쳐져 1회만 나간다", async ({ page }) => {
    const interactionRequests: string[] = [];
    page.on("request", (request) => {
      const url = request.url();
      if (url.includes("/me/interactions") || url.includes("/me/blocked-users")) {
        interactionRequests.push(url);
      }
    });

    await page.goto("/community/posts/1");
    await page.getByRole("button", { name: "이 사용자 차단" }).waitFor();
    await page.waitForLoadState("networkidle");

    expect(interactionRequests).toHaveLength(1);
    expect(interactionRequests[0]).toContain("/me/interactions");
  });
});
