import { test, expect } from './fixtures.js';

/**
 * 번호 추천 화면(설정·결과 두 카드)의 시각 기준선. 비로그인 상태로 본다 — 이 기능은 로그인 여부와 무관하고,
 * 새로 로그인하는 spec은 뒤 spec의 저장된 세션을 풀리게 하므로 쓰지 않는다.
 *
 * 최신 회차 띠(.recommend__latest)는 찍지 않는다 — 시드 이력이 매번 같더라도 날짜·당첨금 같은 값이 섞여 있다.
 * 두 카드(.recommend)만 찍는다. 결과는 라우트를 가로채 번호를 고정한다(서버의 무작위 번호는 기준선이 될 수 없다).
 */
test.use({ storageState: { cookies: [], origins: [] }, viewport: { width: 1280, height: 900 } });

const RESPONSE = {
    strategy: 'reduce_shared_winner_risk',
    algorithmVersion: 'reduce-shared-winner-risk-v1',
    historyThroughRound: 30,
    historicalExclusionApplied: true,
    exclusionPolicyVersion: 'historical-first-prize-v1',
    items: [
        [3, 12, 19, 28, 34, 43],
        [1, 8, 15, 22, 30, 41],
        [5, 11, 24, 33, 38, 45],
        [2, 9, 17, 26, 35, 40],
        [4, 14, 21, 29, 36, 44],
    ].map((numbers, index) => ({ position: index + 1, numbers, score: null, explanationCodes: [] })),
};

async function openWithResults(page) {
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(RESPONSE) });
    });
    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();
    await expect(page.locator('.recommend__item')).toHaveCount(5);
    // 포커스 링이 기준선에 섞이지 않게 한다(성공 후 결과 제목에 포커스가 간다).
    await page.evaluate(() => document.activeElement?.blur());
}

test.describe('번호 추천', () => {
    test('빈 상태 - 설정과 결과 카드', async ({ page }) => {
        await page.goto('/recommend');
        await expect(page.locator('#btn-recommend-generate')).toBeVisible();
        await expect(page.locator('.recommend')).toHaveScreenshot('recommend-empty.png');
    });

    test('결과가 있는 상태', async ({ page }) => {
        await openWithResults(page);
        await expect(page.locator('.recommend')).toHaveScreenshot('recommend-results.png');
    });

    test('다크 모드 - 결과가 있는 상태', async ({ page }) => {
        await page.emulateMedia({ colorScheme: 'dark' });
        await openWithResults(page);
        await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'dark');
        await expect(page.locator('.recommend')).toHaveScreenshot('recommend-results-dark.png');
    });
});
