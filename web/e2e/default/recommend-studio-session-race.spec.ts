import { expect, test } from "@playwright/test";

/**
 * KF-09(docs/improvement.md) — 세션 초기화가 준비성 경쟁과 직렬 워터폴을 만든다.
 *
 * 이전에는 `use-recommend-studio.ts`의 `generate()`에 세션 로딩 게이트가 전혀
 * 없었다. §10 3단계에서 `entities/user-session/session-context.tsx`에
 * `sessionReadiness()`(미확정/익명-준비완료/인증-준비완료 3상태)를 추가하고,
 * `recommend-studio.tsx`가 세션이 `unsettled`인 동안 "조합 만들기" 버튼을
 * `disabled`로 둔다 — Playwright의 클릭 액션은 버튼이 활성화될 때까지 자동으로
 * 기다리므로, 이 테스트의 클릭은 세션이 실제로 정착된 뒤에야 실행된다. `generate()`
 * 자신도 `sessionReady`가 아니면 조용히 반환하는 이중 안전장치를 갖는다(버튼 상태와
 * 무관하게 프로그래매틱 호출에도 안전).
 */
test.describe("세션 미확정 상태에서의 조합 생성 클릭", () => {
  test("세션 응답이 늦어도 CSRF 미준비 오류가 사용자에게 노출되지 않는다", async ({ page }) => {
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
