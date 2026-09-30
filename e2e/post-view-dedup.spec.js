import { test, expect } from './fixtures.js';

test('비로그인 방문자가 서로 다른 글을 연속으로 열어도 정상 응답하고 방문 기록을 유지한다', async ({ page, context }) => {
    await page.goto('/community');
    const links = page.locator('.post-list__title');
    const firstUrl = await links.nth(0).getAttribute('href');
    const secondUrl = await links.nth(1).getAttribute('href');

    expect((await page.goto(firstUrl)).status()).toBe(200);
    expect((await page.goto(secondUrl)).status()).toBe(200);
    const viewedBefore = (await context.cookies()).find(cookie => cookie.name === 'kraft_viewed');
    expect(viewedBefore.value.split('|')).toHaveLength(2);

    expect((await page.goto(firstUrl)).status()).toBe(200);
    const viewedAfter = (await context.cookies()).find(cookie => cookie.name === 'kraft_viewed');
    expect(viewedAfter.value).toBe(viewedBefore.value);
});

test('비로그인 방문자의 쿠키에 범위를 벗어난 시간이 있어도 상세 화면을 열 수 있다', async ({ page, context }) => {
    await page.goto('/community');
    const postUrl = await page.locator('.post-list__title').first().getAttribute('href');
    const postId = postUrl.split('/').pop();
    await context.addCookies([{
        name: 'kraft_viewed',
        value: `${postId}:9223372036854775807`,
        url: new URL(page.url()).origin,
    }]);

    expect((await page.goto(postUrl)).status()).toBe(200);
    const viewed = (await context.cookies()).find(cookie => cookie.name === 'kraft_viewed');
    expect(viewed.value).not.toContain('9223372036854775807');
});
