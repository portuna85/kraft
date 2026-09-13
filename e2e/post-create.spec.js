import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

test('글을 등록하면 목록에 보이고 완료 메시지가 뜬다', async ({ page }) => {
    const title = uniqueTitle('등록');

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('E2E가 작성한 본문입니다.');
    await page.locator('#btn-save').click();

    await page.waitForURL('/');
    await expect(page.locator('#flash')).toContainText('글이 등록되었습니다.');
    await openPostByTitle(page, title);
    await expect(page.locator('#post-title-text')).toHaveText(title);
});

test('제목이 비어 있으면 브라우저 검증이 막고 요청 자체가 나가지 않는다', async ({ page }) => {
    await page.goto('/posts/save');

    let saveRequested = false;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/posts')) {
            saveRequested = true;
        }
    });

    await page.locator('#content').fill('제목 없이 저장을 시도한다.');
    await page.locator('#btn-save').click();

    // required 속성이 submit 이벤트 자체를 막으므로 화면도 그대로다.
    await expect(page).toHaveURL(/\/posts\/save$/);
    expect(saveRequested, '요청이 나가면 안 된다').toBe(false);
});

test('관리자가 아니면 공지 분류를 고를 수 없다', async ({ page }) => {
    await page.goto('/posts/save');

    const options = await page.locator('#category option').allTextContents();
    expect(options).toContain('자유');
    expect(options).not.toContain('공지');
});
