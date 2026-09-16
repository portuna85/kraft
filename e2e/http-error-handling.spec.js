import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * http.js의 응답 해석·타임아웃 경로를 검증한다(개선 보고서 "프런트엔드 오류 분류와 타입 검사
 * 범위"). 예전에는 Content-Type이 JSON이라고 주장하는데 실제로는 깨진 본문이면 JSON.parse가
 * 그대로 던져 원시 SyntaxError가 호출부까지 새어 나갔고, 타임아웃 자체가 없어 응답이 오지
 * 않는 요청은 화면에서 영원히 멈췄다.
 */
test('깨진 JSON 응답은 한국어 오류 메시지로 보여주고 처리되지 않은 JS 오류를 내지 않는다', async ({ page }) => {
    const title = uniqueTitle('오류처리');

    await page.route('**/api/v1/posts', async (route) => {
        if (route.request().method() !== 'POST') {
            await route.continue();
            return;
        }
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: '{이것은-유효한-JSON이-아닙니다',
        });
    });

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('오류 처리 테스트');
    await page.locator('#btn-save').click();

    await expect(page.locator('#flash')).toContainText('서버 응답을 해석할 수 없습니다');
});

test('응답이 오지 않으면 타임아웃 메시지를 보여준다', async ({ page }) => {
    const title = uniqueTitle('타임아웃');

    // 실제 15초를 기다리지 않도록, 응답을 요청 자체가 타임아웃보다 훨씬 오래 지연시킨다.
    // 브라우저 컨텍스트를 벗어나 실제 기본 타임아웃(15초)까지 기다리는 대신, 라우트를 그냥
    // 영원히 보류해 AbortController가 먼저 개입하는지 확인한다.
    await page.route('**/api/v1/posts', async (route) => {
        if (route.request().method() !== 'POST') {
            await route.continue();
            return;
        }
        await new Promise(() => {}); // 응답을 영원히 보류한다 — 타임아웃이 먼저 개입해야 한다.
    });

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('타임아웃 테스트');
    await page.locator('#btn-save').click();

    await expect(page.locator('#flash')).toContainText('요청 시간이 초과되었습니다', { timeout: 20_000 });
});
