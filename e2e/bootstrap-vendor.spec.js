import { test, expect, storageStateFor, openAccountMenu } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * Bootstrap JS를 CDN 대신 이 서버가 직접 제공한다. cdnjs.cloudflare.com으로 가는 요청을 전부 막아도(CDN 장애를 흉내 낸다) 모달·토스트가 그대로 동작해야 한다.
 * <p>
 * 모달은 계정 메뉴의 "비밀번호 변경"으로 연다 — 로그인한 어느 화면에서나 있는 모달이라 글 목록 순서 같은 공유 DB 상태에 기대지 않는다.
 */
test('CDN(cdnjs)을 완전히 막아도 비밀번호 변경 모달이 열리고 닫힌다', async ({ page }) => {
    let cdnRequested = false;
    await page.route('https://cdnjs.cloudflare.com/**', async (route) => {
        cdnRequested = true;
        await route.abort();
    });

    await page.goto('/community');
    await openAccountMenu(page);
    await page.getByRole('button', { name: '비밀번호 변경' }).click();
    await expect(page.locator('#changePasswordModal')).toBeVisible();

    await page.locator('#changePasswordModal .btn-close').click();
    await expect(page.locator('#changePasswordModal')).toBeHidden();

    expect(cdnRequested, 'cdnjs로 가는 요청 자체가 없어야 한다 — Bootstrap JS가 더 이상 거기서 오지 않는다').toBe(false);
});
