import { test, expect } from './fixtures.js';

/**
 * 번호 추천 화면. e2e 프로파일은 당첨 이력 1~30회를 시드한다(E2eDataInitializer) — 서버가 실제로
 * 추천을 만들어 돌려주는 경로를 한 번은 가로채기 없이 검증하고(OPS-15), 나머지는 응답 모양을
 * 결정론적으로 고정하려고 라우트를 가로챈다. 이력이 준비되지 않은 503은 가로채서 만든다.
 *
 * 이 기능은 로그인 여부와 무관하게 동일하게 동작하므로 익명 상태에서 검증한다.
 */
test.use({ storageState: { cookies: [], origins: [] } });

const SUCCESS_RESPONSE = {
    strategy: 'reduce_shared_winner_risk',
    algorithmVersion: 'reduce-shared-winner-risk-v1',
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
    // 화면이 완전히 마운트된 뒤(버튼이 보이고 네트워크가 조용해진 뒤)에도 요청이 없어야 한다. 고정 500ms 대기 대신
    // 마운트·네트워크 안정을 기다린다(OPS-17).
    await expect(page.locator('#btn-recommend-generate')).toBeVisible();
    await page.waitForLoadState('networkidle');

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
    await expect(page.locator('.recommend__item .lotto-ball')).toHaveText(['3', '12', '19', '28', '34', '43']);
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

test('시드된 이력으로 서버가 실제 추천을 만들어 보여준다(가로채기 없음)', async ({ page }) => {
    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();
    await expect(page.locator('.recommend__notice')).toHaveCount(0);
    const items = page.locator('.recommend__item');
    await expect(items).toHaveCount(5);
    for (const item of await items.all()) {
        await expect(item.locator('.lotto-ball')).toHaveCount(6);
    }
});

test('이력이 준비되지 않으면 안내 문구를 보여준다', async ({ page }) => {
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        await route.fulfill({
            status: 503,
            contentType: 'application/json',
            body: JSON.stringify({ code: 'RECOMMENDATION_HISTORY_NOT_READY', detail: '검증된 당첨 이력이 준비되지 않았습니다.' }),
        });
    });
    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__notice')).toContainText('추천 이력이 아직 준비되지 않았습니다.');
});

test('옵션 없이 고정 조건으로 요청한다', async ({ page }) => {
    let body;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        body = route.request().postDataJSON();
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(SUCCESS_RESPONSE) });
    });

    await page.goto('/recommend');
    await expect(page.getByRole('radio')).toHaveCount(0);
    await expect(page.locator('#recommend-count')).toHaveCount(0);

    await page.locator('#btn-recommend-generate').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();

    expect(body).toEqual({
        strategy: 'reduce_shared_winner_risk',
        count: 5,
        lockedNumbers: [],
        excludedNumbers: [],
    });
});

/** F02: 재시도가 실패해도 화면에 남은 이전 결과는 사라지지 않는다. */
test('재시도가 실패해도 이전 결과가 남는다', async ({ page }) => {
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
    await expect(page.locator('.recommend__item .lotto-ball')).toHaveCount(6);
});

test.describe('모바일 메뉴', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('메뉴를 열면 번호 추천 링크가 보인다', async ({ page }) => {
        await page.goto('/');

        await page.locator('#btn-nav-toggle').click();
        await expect(page.locator('#site-nav').getByRole('link', { name: '번호 추천' })).toBeVisible();
    });
});
