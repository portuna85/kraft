import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/** 실제로 디코딩 가능한 1x1 PNG. 서버가 매직 바이트까지 검사하므로 가짜 바이트는 거부된다. */
const PNG_1X1 = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
    'base64',
);

function pngFile(name = 'photo.png') {
    return { name, mimeType: 'image/png', buffer: PNG_1X1 };
}

test('이미지를 고르면 미리보기와 파일명·크기가 나온다', async ({ page }) => {
    await page.goto('/posts/save');

    await page.locator('#picture').setInputFiles(pngFile());

    await expect(page.locator('#picture-preview')).toBeVisible();
    await expect(page.locator('#picture-preview-name')).toContainText('photo.png');
    await expect(page.locator('#picture-preview-image')).toHaveAttribute('src', /^blob:/);
});

test('선택 해제를 누르면 미리보기가 사라진다', async ({ page }) => {
    await page.goto('/posts/save');
    await page.locator('#picture').setInputFiles(pngFile());
    await expect(page.locator('#picture-preview')).toBeVisible();

    await page.locator('#btn-picture-clear').click();

    await expect(page.locator('#picture-preview')).toBeHidden();
});

test('허용하지 않는 확장자는 서버에 보내기 전에 막는다', async ({ page }) => {
    await page.goto('/posts/save');

    await page.locator('#picture').setInputFiles({
        name: 'malware.exe',
        mimeType: 'application/octet-stream',
        buffer: Buffer.from('nope'),
    });

    await expect(page.locator('#flash')).toContainText('JPG, JPEG, PNG, GIF, WEBP');
});

test('5MB를 넘는 파일은 서버에 보내기 전에 막는다', async ({ page }) => {
    await page.goto('/posts/save');

    await page.locator('#picture').setInputFiles({
        name: 'big.png',
        mimeType: 'image/png',
        buffer: Buffer.alloc(5 * 1024 * 1024 + 1),
    });

    await expect(page.locator('#flash')).toContainText('5MB');
});

test('아이폰 HEIC는 이유를 설명하며 막는다', async ({ page }) => {
    await page.goto('/posts/save');

    await page.locator('#picture').setInputFiles({
        name: 'IMG_0001.heic',
        mimeType: 'image/heic',
        buffer: Buffer.from('x'),
    });

    await expect(page.locator('#flash')).toContainText('HEIC');
});

/**
 * 업로드 응답을 기다리는 동안 파일 입력·선택 해제를 다시 누를 수 있으면, 그 사이 사용자가
 * 파일을 바꾸거나 지운 뒤 늦게 도착한 응답이 엉뚱한 파일의 URL로 캐시를 덮어쓸 수 있다
 * (개선 보고서 "업로드 중 파일 교체로 URL 캐시가 다른 파일에 연결될 수 있다"). 입력을
 * 잠그면 그 경쟁 자체가 UI에서 일어나지 않는다.
 */
test('업로드 응답을 기다리는 동안에는 사진 입력과 선택 해제를 다시 누를 수 없다', async ({ page }) => {
    const title = uniqueTitle('업로드중');

    let releaseUpload;
    const gate = new Promise((resolve) => {
        releaseUpload = resolve;
    });
    await page.route('**/api/v1/posts/images', async (route) => {
        await gate;
        await route.continue();
    });

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('업로드 중 잠금 테스트');
    await page.locator('#picture').setInputFiles(pngFile());
    await page.locator('#btn-save').click();

    await expect(page.locator('#picture')).toBeDisabled();
    await expect(page.locator('#btn-picture-clear')).toBeDisabled();

    releaseUpload();
    await page.waitForURL('/');
});

test('이미지를 붙여 글을 등록하면 상세에 그 이미지가 보인다', async ({ page }) => {
    const title = uniqueTitle('이미지');

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('이미지가 붙은 글입니다.');
    await page.locator('#picture').setInputFiles(pngFile());
    await page.locator('#btn-save').click();

    await page.waitForURL('/');
    await openPostByTitle(page, title);
    await expect(page.locator('.post-image img')).toHaveAttribute('src', /^\/images\//);
});

/**
 * "업로드는 성공했는데 저장이 실패한" 경우 같은 파일을 다시 올리지 않고 이미 받은 URL로
 * 재시도한다. 이 동작은 postEdit/postForm 양쪽에 복사되어 있어 리팩터링에서 깨지기 쉬운데,
 * 업로드 요청 수를 세는 것 말고는 확인할 방법이 없다.
 */
test('저장이 실패한 뒤 다시 눌러도 이미지를 재업로드하지 않는다', async ({ page }) => {
    const title = uniqueTitle('재시도');

    let uploadCount = 0;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/posts/images')) {
            uploadCount += 1;
        }
    });

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('재시도 테스트');
    await page.locator('#picture').setInputFiles(pngFile());

    // 첫 시도: 저장만 실패시킨다(업로드는 그대로 통과).
    await page.route('**/api/v1/posts', (route) =>
        route.fulfill({
            status: 500,
            contentType: 'application/problem+json',
            body: JSON.stringify({ detail: '일부러 실패시켰습니다.', status: 500 }),
        }),
    );
    await page.locator('#btn-save').click();
    await expect(page.locator('#flash')).toContainText('일부러 실패시켰습니다.');
    expect(uploadCount, '첫 시도에서 한 번 업로드된다').toBe(1);

    // 두 번째 시도: 저장을 정상으로 되돌리고 같은 파일 그대로 재시도한다.
    await page.unroute('**/api/v1/posts');
    await page.locator('#btn-save').click();

    await page.waitForURL('/');
    expect(uploadCount, '이미 올린 이미지를 다시 올리면 안 된다').toBe(1);
});
