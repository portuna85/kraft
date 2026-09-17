import { test, expect, storageStateFor, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * F08: Bootstrap JS를 CDN 대신 이 서버가 직접 제공한다. cdnjs.cloudflare.com으로 가는
 * 요청을 전부 막아도(CDN 장애를 흉내 낸다) 모달·토스트가 그대로 동작해야 한다 — 예전에는
 * CSS만 자체 호스팅이고 JS는 CDN이라, CDN이 죽으면 모달 없이는 열 수도 닫을 수도 없었다.
 * <p>
 * 신고 버튼은 남의 글에만 보인다(자기 글은 서버도 거절한다). E2eDataInitializer가 심어 둔
 * "다른 사람의 글"을 그대로 쓴다 — 직접 쓴 글로는 다른 스펙들이 만들어 둔 글 목록 순서에
 * 따라 우연히 내 글을 고를 수 있어(공유 DB, 실행 순서 의존) CI에서 실제로 실패했었다.
 */
test('CDN(cdnjs)을 완전히 막아도 신고 모달이 열리고 닫힌다', async ({ page }) => {
    let cdnRequested = false;
    await page.route('https://cdnjs.cloudflare.com/**', async (route) => {
        cdnRequested = true;
        await route.abort();
    });

    await openPostByTitle(page, '다른 사람의 글');

    await page.locator('#btn-report-post').click();
    await expect(page.locator('#reportModal')).toBeVisible();

    await page.locator('#reportModal .btn-close').click();
    await expect(page.locator('#reportModal')).toBeHidden();

    expect(cdnRequested, 'cdnjs로 가는 요청 자체가 없어야 한다 — Bootstrap JS가 더 이상 거기서 오지 않는다').toBe(false);
});
