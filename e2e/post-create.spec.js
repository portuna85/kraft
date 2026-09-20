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

/** 실제로 디코딩 가능한 1x1 PNG. 서버가 매직 바이트까지 검사하므로 가짜 바이트는 거부된다. */
const PNG_1X1 = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
    'base64',
);

/**
 * 이미지 업로드 응답을 기다리는 동안 제목·본문·분류가 계속 활성 상태이면, 그 사이 바꾼 값이
 * 최종 등록 요청에 들어간다(개선 보고서 F03). 입력을 잠그고 제출 시점 값을 스냅샷으로
 * 고정해야 한다.
 */
test('이미지 업로드를 기다리는 동안 입력이 잠기고, 그 사이 바꾼 값은 요청에 들어가지 않는다', async ({ page }) => {
    const submittedTitle = uniqueTitle('업로드중원본');

    let releaseUpload;
    const gate = new Promise((resolve) => {
        releaseUpload = resolve;
    });
    await page.route('**/api/v1/posts/images', async (route) => {
        await gate;
        await route.continue();
    });

    let requestBody = null;
    await page.route('**/api/v1/posts', async (route) => {
        if (route.request().method() === 'POST') {
            requestBody = route.request().postDataJSON();
        }
        await route.continue();
    });

    await page.goto('/posts/save');
    await page.locator('#title').fill(submittedTitle);
    await page.locator('#content').fill('업로드 중 스냅샷 테스트');
    await page.locator('#picture').setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: PNG_1X1 });
    await page.locator('#btn-save').click();

    await expect(page.locator('#title')).toBeDisabled();
    await expect(page.locator('#category')).toBeDisabled();
    await expect(page.locator('#content')).toBeDisabled();

    releaseUpload();
    await page.waitForURL('/');

    expect(requestBody.title, '업로드 중 입력이 잠겨 있으므로 제출 당시 제목과 일치해야 한다')
        .toBe(submittedTitle);
    await openPostByTitle(page, submittedTitle);
    await expect(page.locator('#post-title-text')).toHaveText(submittedTitle);
});
