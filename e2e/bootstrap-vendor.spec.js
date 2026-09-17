import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * F08: Bootstrap JS를 CDN 대신 이 서버가 직접 제공한다. cdnjs.cloudflare.com으로 가는
 * 요청을 전부 막아도(CDN 장애를 흉내 낸다) 모달·토스트가 그대로 동작해야 한다 — 예전에는
 * CSS만 자체 호스팅이고 JS는 CDN이라, CDN이 죽으면 모달 없이는 열 수도 닫을 수도 없었다.
 */
test('CDN(cdnjs)을 완전히 막아도 신고 모달이 열리고 닫힌다', async ({ page }) => {
    let cdnRequested = false;
    await page.route('https://cdnjs.cloudflare.com/**', async (route) => {
        cdnRequested = true;
        await route.abort();
    });

    await page.goto('/posts/save');
    await page.locator('#title').fill(uniqueTitle('부트스트랩벤더'));
    await page.locator('#content').fill('CDN 차단 확인용 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');

    await page.locator('.post-list__title').first().click();

    // 이 글이 내 글이면 신고 버튼이 없다 — 다른 사람의 글로 이동한다.
    if (await page.locator('#btn-report-post').count() === 0) {
        await page.goto('/');
        await page.locator('.post-list__title').filter({ hasNotText: '부트스트랩벤더' }).first().click();
    }

    await page.locator('#btn-report-post').click();
    await expect(page.locator('#reportModal')).toBeVisible();

    await page.locator('#reportModal .btn-close').click();
    await expect(page.locator('#reportModal')).toBeHidden();

    expect(cdnRequested, 'cdnjs로 가는 요청 자체가 없어야 한다 — Bootstrap JS가 더 이상 거기서 오지 않는다').toBe(false);
});
