import { test, expect } from './fixtures.js';

/**
 * 13단계: 게시글 삭제 후 접근(PostNotFoundException → error/not-found)이 아니라, 애초에
 * 어떤 컨트롤러에도 매핑되지 않은 경로를 요청했을 때의 전역 404 화면(error/4xx.html)을
 * 확인한다. 이 흐름은 실제 서블릿 컨테이너의 /error 재디스패치를 거치므로 MockMvc로는
 * 재현되지 않는다(GlobalErrorPageTest의 주석 참고) — 실제 서버로 띄우는 e2e가 유일한
 * 종단 검증이다.
 */
test('없는 경로로 들어가면 공통 404 안내와 게시판 복귀 링크가 뜬다', async ({ page }) => {
    const response = await page.goto('/this-path-does-not-exist-anywhere');

    expect(response.status()).toBe(404);
    await expect(page.locator('.page-title')).toContainText('페이지를 찾을 수 없습니다');

    const backLink = page.getByRole('link', { name: '게시판으로 돌아가기' });
    await expect(backLink).toBeVisible();
    await backLink.click();
    await expect(page).toHaveURL('/');
});
