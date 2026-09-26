import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * 가벼운 마크다운(12단계). 저장 형식은 지금처럼 평문이고, MarkdownToolbar.vue(작성 폼)와
 * MarkdownBody.vue(조회 화면)가 그 평문을 서식으로 보여줄 뿐이다.
 */

test('툴바의 굵게 버튼이 선택한 글자를 **로 감싼다', async ({ page }) => {
    await page.goto('/posts/save');

    const content = page.locator('#content');
    await content.fill('중요한 내용');
    // "중요한"(앞 3글자)만 선택한 상태에서 굵게를 누른다.
    await content.evaluate((el) => el.setSelectionRange(0, 3));
    await page.getByRole('button', { name: '굵게' }).click();

    await expect(content).toHaveValue('**중요한** 내용');
});

test('굵게 마크다운 문법으로 저장하면 상세 화면에 <strong>으로 보인다', async ({ page }) => {
    const title = uniqueTitle('마크다운굵게');

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('**굵은 문장**입니다');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);

    const strong = page.locator('#post-content-text strong');
    await expect(strong).toHaveText('굵은 문장');
    await expect(page.locator('#post-content-text')).toContainText('입니다');
});

test('목록 문법으로 저장하면 상세 화면에 <ul><li>로 보인다', async ({ page }) => {
    const title = uniqueTitle('마크다운목록');

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('- 첫째\n- 둘째');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);

    const items = page.locator('#post-content-text ul li');
    await expect(items).toHaveCount(2);
    await expect(items.nth(0)).toHaveText('첫째');
    await expect(items.nth(1)).toHaveText('둘째');
});

test('작성 중 미리보기로 전환하면 서식이 적용된 모습을 볼 수 있고, 다시 작성으로 돌아갈 수 있다', async ({ page }) => {
    await page.goto('/posts/save');
    await page.locator('#title').fill(uniqueTitle('미리보기'));
    await page.locator('#content').fill('**굵게** 확인');

    await expect(page.locator('#content')).toBeVisible();
    await page.getByRole('button', { name: '미리보기' }).click();

    await expect(page.locator('.markdown-toolbar__preview strong')).toHaveText('굵게');
    // textarea는 미리보기 중에도 DOM에서 사라지지 않는다(1x1px로 시각적으로만 가리므로
    // Playwright 기준으로는 여전히 "visible"이다 — 이 성질 자체가 목적이다. 필수 입력
    // 검증이 계속 걸리는지는 아래 별도 테스트("미리보기 상태에서 내용이 비어 있으면…")가
    // 확인한다).

    await page.getByRole('button', { name: '작성' }).click();
    await expect(page.locator('#content')).toBeVisible();
    await expect(page.locator('#content')).toHaveValue('**굵게** 확인');
});

/**
 * MarkdownToolbar.vue의 textarea는 미리보기 상태에서도 DOM에 남아 required 검증을
 * 받는다(v-show/display:none 대신 시각적으로만 가리는 이유) — 실제로 브라우저가 빈 값을
 * 막는지 확인한다.
 */
test('미리보기 상태에서 내용이 비어 있으면 등록을 눌러도 브라우저 검증이 막는다', async ({ page }) => {
    await page.goto('/posts/save');
    await page.locator('#title').fill(uniqueTitle('미리보기검증'));

    await page.getByRole('button', { name: '미리보기' }).click();

    let saveRequested = false;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/posts')) {
            saveRequested = true;
        }
    });

    await page.locator('#btn-save').click();

    await expect(page).toHaveURL(/\/posts\/save$/);
    expect(saveRequested, '내용이 비어 있으면 요청이 나가면 안 된다').toBe(false);
});

test('수정 화면에서도 같은 툴바로 서식을 적용할 수 있다', async ({ page }) => {
    const title = uniqueTitle('수정마크다운');

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('평범한 내용');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);

    await page.locator('#btn-edit').click();
    await page.locator('#content').fill('*기울인* 내용으로 수정');
    await page.locator('#btn-update').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);

    const em = page.locator('#post-content-text em');
    await expect(em).toHaveText('기울인');
});
