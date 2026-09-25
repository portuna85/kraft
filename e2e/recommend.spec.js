import { test, expect } from './fixtures.js';

/**
 * 번호 추천 화면. 이 저장소의 e2e 프로파일은 Flyway를 돌리지 않고
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

/**
 * 문서 5.5: 처리 중 상태를 화면으로 보는 사용자에게도 알려야 한다. 예전에는 버튼 문구
 * 변경만 있고 화면 낭독기 전용 안내(aria-live)만 있었다.
 */
test('생성 중에는 버튼 아래 안내가 보이고, 끝나면 사라진다', async ({ page }) => {
    let releaseResponse;
    const held = new Promise((resolve) => {
        releaseResponse = resolve;
    });
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        await held;
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SUCCESS_RESPONSE) });
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    const progress = page.locator('.form-progress');
    await expect(progress).toBeVisible();
    await expect(progress).toContainText('추천 번호를 생성하는 중입니다.');

    releaseResponse();
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();
    await expect(progress).toHaveCount(0);
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

/**
 * F02: 성공 후 조건을 바꾸고 재시도했다가 실패해도, 화면에 남아있는 이전 결과 옆의 조건 변경
 * 안내가 사라지면 안 된다(status가 'ready'가 아니게 된다고 안내까지 꺼지던 결함).
 */
test('조건을 바꾸고 재시도가 실패해도 이전 결과와 조건 변경 안내가 함께 남는다', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        requestCount += 1;
        if (requestCount === 1) {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SUCCESS_RESPONSE) });
            return;
        }
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: '일시적인 오류' }) });
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();

    await page.getByRole('radio', { name: '조건 내 무작위' }).check();
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__error')).toContainText('일시적인 오류');
    await expect(page.locator('.recommend__item-numbers')).toContainText('3, 12, 19, 28, 34, 43');
    await expect(page.locator('.recommend__stale')).toContainText('조건이 변경되었습니다.');
});

/** F02: 재시도가 이력 미준비(503)로 끝나는 경우도 같은 방식으로 안내가 유지되어야 한다. */
test('조건을 바꾸고 재시도가 이력 미준비로 끝나도 조건 변경 안내가 남는다', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        requestCount += 1;
        if (requestCount === 1) {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SUCCESS_RESPONSE) });
            return;
        }
        await route.fulfill({
            status: 503,
            contentType: 'application/json',
            body: JSON.stringify({ code: 'RECOMMENDATION_HISTORY_NOT_READY', detail: '이력 준비 중' }),
        });
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();

    await page.locator('#recommend-count').fill('3');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__notice')).toContainText('추천 이력이 아직 준비되지 않았습니다.');
    await expect(page.locator('.recommend__item-numbers')).toContainText('3, 12, 19, 28, 34, 43');
    await expect(page.locator('.recommend__stale')).toContainText('조건이 변경되었습니다.');
});

/** F02: 조건을 바꾸지 않고 재시도해 실패한 경우에는 잘못된 조건 변경 안내를 띄우면 안 된다. */
test('같은 조건으로 재시도가 실패해도 잘못된 조건 변경 안내를 보이지 않는다', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        requestCount += 1;
        if (requestCount === 1) {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SUCCESS_RESPONSE) });
            return;
        }
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: '일시적인 오류' }) });
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();

    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__error')).toContainText('일시적인 오류');
    await expect(page.locator('.recommend__stale')).toHaveCount(0);
});

test.describe('모바일 메뉴', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('메뉴를 열면 번호 추천 링크가 보인다', async ({ page }) => {
        await page.goto('/');

        await page.locator('#btn-nav-toggle').click();
        await expect(page.locator('#site-nav').getByRole('link', { name: '번호 추천' })).toBeVisible();
    });
});
