import { test, expect } from './fixtures.js';

/**
 * 번호 추천 화면(02문서 8절, 03문서 3절). 이 저장소의 e2e 프로파일은 Flyway를 돌리지 않고
 * 엔티티로 스키마만 만들므로(application-e2e.yml, ddl-auto: create-drop)
 * recommendation_history_state 시드 행이 없다 — 서버는 항상 503
 * RECOMMENDATION_HISTORY_NOT_READY로 응답한다(RecommendationHistoryProvider). 실제 성공
 * 응답 경로는 라우트 가로채기로 결정론적으로 검증한다.
 *
 * 이 기능은 로그인 여부와 무관하게 동일하게 동작하므로 익명 상태에서 검증한다.
 */
test.use({ storageState: { cookies: [], origins: [] } });

const SUCCESS_RESPONSE = {
    strategy: 'balanced',
    algorithmVersion: 'balanced-v1',
    historyThroughRound: 120,
    historicalExclusionApplied: true,
    exclusionPolicyVersion: 'historical-first-prize-v1',
    items: [
        {
            position: 1,
            numbers: [3, 12, 19, 28, 34, 43],
            score: 5,
            explanationCodes: ['ODD_EVEN_BALANCED', 'LOW_HIGH_BALANCED', 'SUM_IN_RANGE'],
        },
    ],
};

test('진입 시 자동으로 생성 요청을 보내지 않는다', async ({ page }) => {
    let requested = false;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        requested = true;
        await route.continue();
    });

    await page.goto('/recommend');
    await page.waitForTimeout(500);

    expect(requested).toBe(false);
});

test('생성 버튼을 누르면 정확히 한 번 요청하고 결과를 보여준다', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        requestCount += 1;
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SUCCESS_RESPONSE) });
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();
    await expect(page.locator('.recommend__item-numbers')).toContainText('3, 12, 19, 28, 34, 43');
    expect(requestCount).toBe(1);

    // 성공 후 포커스가 결과 제목으로 이동한다.
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeFocused();
});

test('이력이 준비되지 않으면 안내 문구를 보여준다(이 저장소의 기본 e2e 상태)', async ({ page }) => {
    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__notice')).toContainText('추천 이력이 아직 준비되지 않았습니다.');
});

test('번호 그리드와 생성 버튼은 키보드만으로 조작할 수 있다', async ({ page }) => {
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SUCCESS_RESPONSE) });
    });

    await page.goto('/recommend');

    const numberOne = page.getByRole('button', { name: '1, 미선택', exact: true });
    await numberOne.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('button', { name: '1, 고정됨' })).toBeVisible();

    await page.locator('#btn-recommend-generate').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();
});

test.describe('모바일 메뉴', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('메뉴를 열면 번호 추천 링크가 보인다', async ({ page }) => {
        await page.goto('/');

        await page.locator('#btn-nav-toggle').click();
        await expect(page.locator('#site-nav').getByRole('link', { name: '번호 추천' })).toBeVisible();
    });
});
