import { test, expect } from './fixtures.js';

/**
 * 번호 추천 화면. e2e 프로파일은 당첨 이력 1~30회를 시드한다(E2eDataInitializer) — 서버가 실제로
 * 추천을 만들어 돌려주는 경로를 한 번은 가로채기 없이 검증하고(OPS-15), 나머지는 응답 모양을
 * 결정론적으로 고정하려고 라우트를 가로챈다. 이력이 준비되지 않은 503은 가로채서 만든다.
 *
 * 이 기능은 로그인 여부와 무관하게 동일하게 동작하므로 익명 상태에서 검증한다.
 */
test.use({ storageState: { cookies: [], origins: [] } });

const DEFAULT_BODY = { count: 5 };

/** count개 조합을 담은 성공 응답. 번호는 조합마다 달라 복사 내용을 가릴 수 있다. */
function successResponse(count = 1) {
    const sets = [
        [3, 12, 19, 28, 34, 43],
        [1, 8, 15, 22, 30, 41],
        [5, 11, 24, 33, 38, 45],
        [2, 9, 17, 26, 35, 40],
        [4, 14, 21, 29, 36, 44],
    ];
    return {
        algorithmVersion: 'uniform-random-v1',
        historyThroughRound: 120,
        historicalExclusionApplied: true,
        exclusionPolicyVersion: 'historical-first-prize-v1',
        items: sets.slice(0, count).map((numbers, index) => ({
            position: index + 1,
            numbers,
        })),
    };
}

async function fulfillWith(route, body) {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

test('진입 시 자동으로 생성 요청을 보내지 않고, 결과 자리에는 빈 상태가 보인다', async ({ page }) => {
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
    // 결과 제목은 비어 있을 때도 있다(성공 후 포커스가 그리로 간다). 빈 상태 안내가 대신 보인다.
    await expect(page.getByRole('heading', { name: '추천 결과' })).toBeVisible();
    await expect(page.getByText('새로운 조합을 기다리고 있어요')).toBeVisible();
    await expect(page.locator('.recommend__item')).toHaveCount(0);
});

test('생성 버튼을 누르면 정확히 한 번 요청하고 결과를 보여준다', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        requestCount += 1;
        await fulfillWith(route, successResponse(1));
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__item .lotto-ball')).toHaveText(['3', '12', '19', '28', '34', '43']);
    await expect(page.getByText('새로운 조합을 기다리고 있어요')).toHaveCount(0);
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
        await fulfillWith(route, successResponse(1));
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    const progress = page.locator('.form-progress');
    await expect(progress).toBeVisible();
    await expect(progress).toContainText('추천 번호를 생성하는 중입니다.');
    await expect(page.locator('#btn-recommend-generate')).toBeDisabled();

    releaseResponse();
    await expect(page.locator('.recommend__item')).toHaveCount(1);
    await expect(progress).toHaveCount(0);
});

test('시드된 이력으로 서버가 실제 추천을 만들어 보여준다(가로채기 없음)', async ({ page }) => {
    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__item').first()).toBeVisible();
    await expect(page.locator('.recommend__notice')).toHaveCount(0);
    const items = page.locator('.recommend__item');
    await expect(items).toHaveCount(5);
    for (const item of await items.all()) {
        await expect(item.locator('.lotto-ball')).toHaveCount(6);
    }
    // 서버가 돌려준 검증 회차가 각주에 보인다.
    await expect(page.locator('.recommend__history-basis')).toContainText('1~30회 1등 당첨 조합을 제외했습니다.');
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

test('추천 방식 선택은 없고 개수 선택만 있으며, 기본 요청은 5개다', async ({ page }) => {
    let body;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        body = route.request().postDataJSON();
        await fulfillWith(route, successResponse(1));
    });

    await page.goto('/recommend');
    await expect(page.getByRole('radio')).toHaveCount(0);
    await expect(page.getByText(/1등 당첨 조합 제외/).first()).toBeVisible();
    await expect(page.locator('#recommend-count')).toHaveValue('5');

    await page.locator('#btn-recommend-generate').focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('.recommend__item')).toHaveCount(1);

    expect(body).toEqual(DEFAULT_BODY);
});

test('고른 개수가 요청에 그대로 실린다', async ({ page }) => {
    let body;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        body = route.request().postDataJSON();
        await fulfillWith(route, successResponse(3));
    });

    await page.goto('/recommend');
    await page.locator('#recommend-count').selectOption('3');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__item')).toHaveCount(3);
    expect(body).toEqual({ count: 3 });
});

test('결과 행에는 A, B, C… 라벨이 붙고 숫자는 보조기기에 읽힌다', async ({ page }) => {
    await page.route('**/api/v1/numbers/recommend', async (route) => fulfillWith(route, successResponse(3)));

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    const letters = page.locator('.recommend__letter');
    await expect(letters).toHaveText(['A', 'B', 'C']);
    // 공은 장식(aria-hidden)이고, 같은 내용을 숨겨진 글자가 읽어 준다.
    await expect(page.locator('.recommend__item').first().locator('.visually-hidden'))
        .toHaveText('추천 A: 3, 12, 19, 28, 34, 43');
});

test.describe('복사', () => {
    /** 클립보드를 가짜로 바꿔 쓴 값을 읽는다. 권한 프롬프트나 실제 클립보드에 기대지 않는다. */
    async function stubClipboard(page) {
        await page.addInitScript(() => {
            window.__copied = [];
            Object.defineProperty(navigator, 'clipboard', {
                configurable: true,
                value: { writeText: async (text) => { window.__copied.push(text); } },
            });
        });
    }

    test('행의 복사 버튼은 그 조합만 쉼표로 복사하고 토스트로 알린다', async ({ page }) => {
        await stubClipboard(page);
        await page.route('**/api/v1/numbers/recommend', async (route) => fulfillWith(route, successResponse(3)));
        await page.goto('/recommend');
        await page.locator('#btn-recommend-generate').click();

        await page.getByRole('button', { name: 'B 조합 복사' }).click();

        expect(await page.evaluate(() => window.__copied)).toEqual(['1, 8, 15, 22, 30, 41']);
        await expect(page.locator('#app-toast-body')).toContainText('B 조합을 복사했습니다.');
    });

    test('전체 복사는 한 줄에 한 조합으로 모두 복사한다', async ({ page }) => {
        await stubClipboard(page);
        await page.route('**/api/v1/numbers/recommend', async (route) => fulfillWith(route, successResponse(3)));
        await page.goto('/recommend');
        await expect(page.locator('#btn-recommend-copy-all')).toHaveCount(0);
        await page.locator('#btn-recommend-generate').click();

        await page.locator('#btn-recommend-copy-all').click();

        expect(await page.evaluate(() => window.__copied)).toEqual([
            '3, 12, 19, 28, 34, 43\n1, 8, 15, 22, 30, 41\n5, 11, 24, 33, 38, 45',
        ]);
        await expect(page.locator('#app-toast-body')).toContainText('전체 조합을 복사했습니다.');
    });

    test('클립보드를 쓸 수 없으면 실패를 토스트로 알린다', async ({ page }) => {
        await page.addInitScript(() => {
            Object.defineProperty(navigator, 'clipboard', {
                configurable: true,
                value: { writeText: async () => { throw new Error('NotAllowedError'); } },
            });
            document.execCommand = () => false;
        });
        await page.route('**/api/v1/numbers/recommend', async (route) => fulfillWith(route, successResponse(1)));
        await page.goto('/recommend');
        await page.locator('#btn-recommend-generate').click();

        await page.getByRole('button', { name: 'A 조합 복사' }).click();

        await expect(page.locator('#app-toast-body')).toContainText('복사하지 못했습니다.');
    });
});

/** F02: 재시도가 실패해도 화면에 남은 이전 결과는 사라지지 않는다. */
test('재시도가 실패해도 이전 결과가 남는다', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        requestCount += 1;
        if (requestCount === 1) {
            await fulfillWith(route, successResponse(1));
            return;
        }
        await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: '일시적인 오류' }) });
    });

    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();
    await expect(page.locator('.recommend__item')).toHaveCount(1);

    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__error')).toContainText('일시적인 오류');
    await expect(page.locator('.recommend__item .lotto-ball')).toHaveCount(6);
});

test('생성 한도 오류에는 개수를 줄이라는 안내가 덧붙는다', async ({ page }) => {
    await page.route('**/api/v1/numbers/recommend', async (route) => {
        await route.fulfill({
            status: 503,
            contentType: 'application/json',
            body: JSON.stringify({ code: 'RECOMMENDATION_GENERATION_LIMIT_REACHED', detail: '조건에 맞는 조합을 모두 만들지 못했습니다.' }),
        });
    });
    await page.goto('/recommend');
    await page.locator('#btn-recommend-generate').click();

    await expect(page.locator('.recommend__error')).toContainText('조건에 맞는 조합을 모두 만들지 못했습니다.');
    await expect(page.locator('.recommend__error')).toContainText('개수를 줄여 보세요');
});

test.describe('모바일', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('메뉴를 열면 번호 추천 링크가 보인다', async ({ page }) => {
        await page.goto('/');

        await page.locator('#btn-nav-toggle').click();
        await expect(page.locator('#site-nav').getByRole('link', { name: '번호 추천' })).toBeVisible();
    });

    test('두 카드가 위아래로 쌓이고 가로로 넘치지 않는다', async ({ page }) => {
        await page.route('**/api/v1/numbers/recommend', async (route) => fulfillWith(route, successResponse(5)));
        await page.goto('/recommend');
        await page.locator('#btn-recommend-generate').click();
        await expect(page.locator('.recommend__item')).toHaveCount(5);

        const settings = await page.locator('#recommend-settings-title').boundingBox();
        const results = await page.locator('#recommend-results-title').boundingBox();
        expect(results.y).toBeGreaterThan(settings.y);
        // 두 제목이 같은 줄이 아니다(= 위아래로 쌓였다).
        expect(Math.abs(results.x - settings.x)).toBeLessThan(5);

        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflow).toBeLessThanOrEqual(0);
    });
});

test('넓은 화면에서는 설정과 결과가 나란히 놓인다', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/recommend');

    const settings = await page.locator('#recommend-settings-title').boundingBox();
    const results = await page.locator('#recommend-results-title').boundingBox();
    expect(results.x).toBeGreaterThan(settings.x + 200);
    expect(Math.abs(results.y - settings.y)).toBeLessThan(5);
});
