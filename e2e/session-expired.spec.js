import { test, expect, storageStateFor, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * 세션이 끊긴 뒤의 동작을 고정한다. jQuery를 걷어내고 fetch로 옮길 때 가장 놓치기 쉬운 지점이다.
 *
 * 실제로 무슨 일이 일어나는지 확인해 보니 예상과 달랐다. 세션이 사라지면 로그인 페이지로
 * 리다이렉트되는 것이 아니라 **403**이 난다:
 *
 *   CSRF 토큰은 세션에 저장된다(Spring Security 기본 HttpSessionCsrfTokenRepository).
 *   세션이 없어지면 토큰을 대조할 곳이 없으므로 CsrfFilter가 인증 진입점보다 **먼저** 거절한다.
 *   즉 이 앱에서 변경 요청은 "미인증 → /login 리다이렉트"에 도달하지 못한다.
 *
 * 그래서 사용자가 보는 문구는 "로그인이 필요합니다"가 아니라 아래의 403 안내다. 권한 부족과
 * 세션·토큰 만료를 함께 언급하는 현재 문구가 이 상황에 맞다.
 *
 * 리다이렉트로 200 + 로그인 HTML이 돌아오는 경로는 코드에 방어적으로 남아 있지만(프록시·
 * 설정 변경으로 되살아날 수 있다) 지금의 설정에서는 이 경로가 먼저 잡는다.
 */
test('세션이 끊긴 뒤 추천을 누르면 다시 로그인하라고 안내한다', async ({ page }) => {
    await openPostByTitle(page, '테스터의 글');
    await expect(page.locator('#btn-like')).toBeVisible();

    // 다른 기기에서 로그아웃했거나 세션이 만료된 상황.
    await page.context().clearCookies();

    await page.locator('#btn-like').click();

    await expect(page.locator('#app-toast')).toContainText('다시 로그인해 주세요');
    // 조용히 성공한 것처럼 넘어가면 안 된다 — 추천 수는 그대로여야 한다.
    await expect(page.locator('#like-count')).toHaveText('0');
});
