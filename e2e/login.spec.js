import { test, expect } from './fixtures.js';

test.use({ storageState: { cookies: [], origins: [] } });

/**
 * F13: 이메일·비밀번호에 required가 없어 빈 값도 서버로 제출됐다. 브라우저 검증이 먼저
 * 막는지 확인한다.
 */
test('이메일·비밀번호가 비어 있으면 브라우저 검증이 막고 요청 자체가 나가지 않는다', async ({ page }) => {
    let requested = false;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/login')) {
            requested = true;
        }
    });

    await page.goto('/login');
    await page.getByRole('button', { name: '로그인' }).click();

    expect(requested, 'required가 제출 자체를 막는다').toBe(false);
    await expect(page).toHaveURL(/\/login$/);
});
