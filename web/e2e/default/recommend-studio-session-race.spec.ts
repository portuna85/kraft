import { expect, test } from "@playwright/test";

/**
 * KF-09(docs/improvement.md) — 세션 초기화가 준비성 경쟁과 직렬 워터폴을 만든다.
 *
 * `use-recommend-studio.ts`의 `generate()`는 `recommend-history-list.tsx:59-60`의
 * `if (session.loading) return;`와 달리 세션 로딩 게이트가 전혀 없다. CSRF 쿠키는
 * 세션 조회 응답(`/api/v1/community/session`)의 `Set-Cookie`로 심어지는데
 * (`transport.ts`의 `browserMutate`가 쿠키 없으면 `CSRF_TOKEN_MISSING`으로 즉시
 * 거부), 세션 응답이 느린 연결에서 늦게 도착하면 그 사이 "조합 만들기"를 클릭한
 * 사용자는 원인과 무관한 "보안 토큰이 없어 요청을 보낼 수 없습니다" 오류를 본다.
 *
 * **이 테스트는 지금 실패해야 정상이다(red).** 세션이 아직 준비되지 않은 동안
 * 생성 액션이 안전하게 게이팅되도록(KF-01과 함께 §미확정/익명-준비완료/인증-준비완료
 * 3상태로 설계) 고치면 통과해야 한다.
 */
test.describe("세션 미확정 상태에서의 조합 생성 클릭", () => {
  test("세션 응답이 늦어도 CSRF 미준비 오류가 사용자에게 노출되지 않는다", async ({ page }) => {
    test.fail(
      true,
      "KF-09: generate()에 세션 로딩 게이트가 없어 CSRF 오류가 노출됨 — 근본 수정(§10 3단계) 전까지 알려진 실패",
    );
    await page.route("**/api/v1/community/session", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 500));
      await route.continue();
    });

    await page.goto("/recommend");
    // 의도적으로 세션 조회(및 그 결과인 XSRF 쿠키)를 기다리지 않고 바로 클릭한다 —
    // 느린 연결에서 사용자가 빠르게 클릭하는 상황을 재현한다.
    await page.getByRole("button", { name: "조합 만들기" }).click();

    await expect(page.getByText("보안 토큰이 없어 요청을 보낼 수 없습니다")).not.toBeVisible({
      timeout: 1000,
    });
  });
});
