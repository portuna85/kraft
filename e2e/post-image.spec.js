import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

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
    await page.waitForURL(/\/posts\/update\/\d+$/);
});

test('이미지를 붙여 글을 등록하면 상세에 그 이미지가 보인다', async ({ page }) => {
    const title = uniqueTitle('이미지');

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('이미지가 붙은 글입니다.');
    await page.locator('#picture').setInputFiles(pngFile());
    await page.locator('#btn-save').click();

    // 등록 후 목록이 아니라 방금 쓴 글로 바로 이동한다(전체 리뷰 2026-09-26 A-FE-02).
    await page.waitForURL(/\/posts\/update\/\d+$/);
    const img = page.locator('.post-image img');
    await expect(img).toHaveAttribute('src', /^\/images\//);
    // A-FE-09: 서버가 검증 때 읽은 실제 픽셀 크기(1×1 고정 픽스처)가 CLS 방지용
    // width/height로 그대로 내려온다 — eager 로딩·높은 우선순위로도 바뀐다.
    await expect(img).toHaveAttribute('width', '1');
    await expect(img).toHaveAttribute('height', '1');
    await expect(img).toHaveAttribute('loading', 'eager');
    await expect(img).toHaveAttribute('fetchpriority', 'high');
});

test('A-FE-09: 이미지를 그대로 두고 제목만 고치면 크기 정보가 그대로 남는다', async ({ page }) => {
    const title = uniqueTitle('이미지수정');

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('이미지가 붙은 글입니다.');
    await page.locator('#picture').setInputFiles(pngFile());
    await page.locator('#btn-save').click();
    await page.waitForURL(/\/posts\/update\/\d+$/);

    await page.locator('#btn-edit').click();
    await page.locator('#title').fill(`${title}-수정`);
    await page.locator('#btn-update').click();
    await page.waitForURL(/\/posts\/update\/\d+$/);

    const img = page.locator('.post-image img');
    await expect(img).toHaveAttribute('width', '1');
    await expect(img).toHaveAttribute('height', '1');
});

/**
 * A-FE-06: 5MB를 훌쩍 넘는 원본도 브라우저에서 축소된 뒤 통과한다.
 *
 * 업로드 파일은 무압축 BMP(가로세로 3000×2000, 약 18MB)다. 확장자는 .png로 속이지만
 * 문제가 되지 않는다 — 브라우저의 createImageBitmap은 파일 이름이 아니라 실제 바이트로
 * 형식을 판별하므로 BMP로 정상 디코딩되고, 축소 결과는 canvas.toBlob이 새로 만든 진짜
 * PNG라 서버 검증(매직 바이트)도 통과한다. 2048px가 넘는 원본이라 축소 경로를 반드시
 * 타는 크기로 골랐다.
 */
function hugeBmpDisguisedAsPng() {
    const width = 3000;
    const height = 2000;
    const rowSize = width * 3;
    const rowPadded = Math.ceil(rowSize / 4) * 4;
    const pixelDataSize = rowPadded * height;
    const fileSize = 54 + pixelDataSize;

    const buffer = Buffer.alloc(fileSize);
    buffer.write('BM', 0, 'ascii');
    buffer.writeUInt32LE(fileSize, 2);
    buffer.writeUInt32LE(54, 10); // 픽셀 데이터 시작 오프셋
    buffer.writeUInt32LE(40, 14); // BITMAPINFOHEADER 크기
    buffer.writeInt32LE(width, 18);
    buffer.writeInt32LE(height, 22); // 양수 = 아래에서 위로 저장
    buffer.writeUInt16LE(1, 26); // 색상 평면
    buffer.writeUInt16LE(24, 28); // 픽셀당 24비트(BGR)
    buffer.writeUInt32LE(0, 30); // BI_RGB(무압축)

    let offset = 54;
    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            buffer[offset++] = x % 256; // B
            buffer[offset++] = y % 256; // G
            buffer[offset++] = (x + y) % 256; // R
        }
        offset += rowPadded - rowSize; // 4바이트 정렬 패딩
    }

    return { name: 'huge.png', mimeType: 'image/png', buffer };
}

test('가로세로 2048px를 넘는 큰 이미지는 브라우저에서 축소된 뒤 첨부·등록된다', async ({ page }) => {
    const title = uniqueTitle('큰이미지');
    const huge = hugeBmpDisguisedAsPng();
    expect(huge.buffer.length).toBeGreaterThan(5 * 1024 * 1024);

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('축소돼서 올라가는 큰 이미지입니다.');
    await page.locator('#picture').setInputFiles(huge);

    // 원본은 5MB를 훌쩍 넘지만 축소 후 크기로 검사하므로 거절 배너가 뜨지 않는다.
    await expect(page.locator('#picture-preview')).toBeVisible();
    await expect(page.locator('#flash')).not.toContainText('5MB');

    await page.locator('#btn-save').click();

    await page.waitForURL(/\/posts\/update\/\d+$/);
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

    await page.waitForURL(/\/posts\/update\/\d+$/);
    expect(uploadCount, '이미 올린 이미지를 다시 올리면 안 된다').toBe(1);
});

/**
 * 파일을 고른 직후 축소가 끝나기 전에는 file이 비어 있어, 그 사이 제출하면 사진 없이
 * 저장됐다(FE-02). 축소 중에는 제출 버튼을 막고, 끝나면 풀어야 한다.
 */
test('이미지 축소가 끝나기 전에는 등록 버튼이 잠기고 끝나면 풀린다', async ({ page }) => {
    // 모바일처럼 느린 축소를 흉내 낸다 — 테스트가 풀어 줄 때까지 createImageBitmap을 보류한다.
    await page.addInitScript(() => {
        const original = window.createImageBitmap.bind(window);
        window.__releaseResize = () => {};
        window.createImageBitmap = (...args) => new Promise((resolve, reject) => {
            window.__releaseResize = () => original(...args).then(resolve, reject);
        });
    });

    await page.goto('/posts/save');
    await page.locator('#title').fill(uniqueTitle('축소중'));
    await page.locator('#content').fill('축소 중 제출 방지');

    await page.locator('#picture').setInputFiles(pngFile());

    await expect(page.locator('#btn-save')).toBeDisabled();
    await expect(page.locator('#picture-preview')).toBeHidden();

    await page.evaluate(() => window.__releaseResize());

    await expect(page.locator('#picture-preview')).toBeVisible();
    await expect(page.locator('#btn-save')).toBeEnabled();
});
