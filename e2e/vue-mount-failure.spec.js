import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * F12: 초기 부트스트랩 JSON이 없거나 깨졌거나 모양이 다르면, mount.js가 검증 없이 넘겨
 * Vue 컴포넌트가 곧바로 죽어 마운트 지점이 빈 채 남았다. 이제는 그 자리에 최소 안내를 남긴다.
 */
test('게시글 상세의 초기 JSON이 깨지면 빈 화면 대신 안내가 뜬다', async ({ page }) => {
    const title = uniqueTitle('마운트실패');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('마운트 실패 재현용 본문');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
    const postUrl = page.url();

    await page.route(/\/posts\/update\/\d+$/, async (route) => {
        const response = await route.fetch();
        const body = await response.text();
        await route.fulfill({
            response,
            body: body.replace(
                /(<script type="application\/json" id="post-initial-data"[^>]*>)([^<]*)/,
                '$1{이것은-잘못된-JSON입니다',
            ),
        });
    });

    await page.goto(postUrl);

    await expect(page.locator('#post-app .flash--danger')).toContainText('새로고침해 주세요');
    // 서버가 먼저 그린 본문은 지우지 않는다 — 조작은 못 해도 글은 읽을 수 있다(F08).
    await expect(page.locator('#post-app [data-ssr-content] .post-title')).toHaveText(title);
    await expect(page.locator('#post-app [data-ssr-content] .post-body')).toHaveText('마운트 실패 재현용 본문');
});

/** 같은 문제, 댓글 쪽. 댓글 영역만 비어야 하고 나머지 화면(게시글 본문 등)은 멀쩡해야 한다. */
test('댓글의 초기 JSON이 깨지면 댓글 영역에만 안내가 뜨고 게시글은 정상 표시된다', async ({ page }) => {
    const title = uniqueTitle('댓글마운트실패');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('댓글 마운트 실패 재현용 본문');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
    const postUrl = page.url();

    await page.route(/\/posts\/update\/\d+$/, async (route) => {
        const response = await route.fetch();
        const body = await response.text();
        await route.fulfill({
            response,
            body: body.replace(
                /(<script type="application\/json" id="comments-initial-data"[^>]*>)([^<]*)/,
                '$1{이것은-잘못된-JSON입니다',
            ),
        });
    });

    await page.goto(postUrl);

    await expect(page.locator('#post-title-text')).toHaveText(title);
    await expect(page.locator('#comments-app .flash--danger')).toContainText('새로고침해 주세요');
});

/**
 * F12: 진입 스크립트 자체(또는 그 스크립트가 정적으로 import하는 공유 청크)가 404 등으로
 * 못 오면 mount.js 코드 자체가 실행되지 않는다 — try/catch로 잡을 수 없는 경로다. 모듈
 * 스크립트의 onerror가 같은 안내를 남기는지 확인한다.
 */
test('추천 화면의 진입 스크립트가 404면 빈 화면 대신 안내가 뜬다', async ({ page }) => {
    await page.route('**/js/vue-dist/recommend.js', (route) => route.fulfill({ status: 404, body: 'not found' }));

    await page.goto('/recommend');

    await expect(page.locator('#recommend-app .flash--danger')).toContainText('새로고침해 주세요');
});

/** 공유 런타임 청크가 못 오는 경우도 같은 onerror 경로로 잡힌다(모듈 그래프 전체 실패). */
test('공유 런타임 청크가 404면 빈 화면 대신 안내가 뜬다', async ({ page }) => {
    await page.route('**/js/vue-dist/chunks/runtime.js', (route) => route.fulfill({ status: 404, body: 'not found' }));

    await page.goto('/signup');

    await expect(page.locator('#signup-app .flash--danger')).toContainText('새로고침해 주세요');
});
