import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

async function createOwnPost(page, title, content) {
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill(content);
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
}

/**
 * 13단계: 서버가 먼저 그리는 읽기 전용 본문(Vue가 마운트되기 전·마운트 실패 시 보이는
 * 화면, post-update.html의 post-ssr)도 마크다운을 해석해서 보여줘야 한다. 예전에는
 * 이 자리에 원문을 그대로 찍어 `**굵게**` 같은 문법이 그대로 보였다.
 */
test('JS 없이도 서버가 그린 본문에 마크다운 서식이 적용돼 있다', async ({ page, browser }) => {
    const title = uniqueTitle('SSR마크다운');
    await createOwnPost(page, title, '**굵게**와 *기울임*, `코드`, [카카오](https://kakao.com)\n\n- 목록1\n- 목록2');
    const postUrl = page.url();

    const noJs = await browser.newContext({ storageState: storageStateFor('user'), javaScriptEnabled: false });
    try {
        const plain = await noJs.newPage();
        await plain.goto(postUrl);
        const body = plain.locator('#post-app .post-body');

        await expect(body.locator('strong')).toHaveText('굵게');
        await expect(body.locator('em')).toHaveText('기울임');
        await expect(body.locator('code')).toHaveText('코드');
        const link = body.locator('a', { hasText: '카카오' });
        await expect(link).toHaveAttribute('href', 'https://kakao.com');
        await expect(link).toHaveAttribute('target', '_blank');
        await expect(link).toHaveAttribute('rel', /nofollow/);
        await expect(body.locator('ul li')).toHaveCount(2);

        // 마크다운 문법 문자 자체는 화면에 남지 않는다(평문 그대로 저장돼 있을 뿐이다).
        await expect(body).not.toContainText('**굵게**');
    } finally {
        await noJs.close();
    }
});

/**
 * HTML 태그가 든 본문도 해석하지 않고 이스케이프한 채 글자 그대로 보여야 한다(XSS 방지).
 * `<script>`는 컨텐츠 정책상 실행되면 안 되므로, 실제로 실행되지 않고 글자로만 보이는지
 * 확인한다.
 */
test('JS 없이도 본문의 HTML 태그가 해석되지 않고 글자 그대로 보인다', async ({ page, browser }) => {
    const title = uniqueTitle('SSR이스케이프');
    await createOwnPost(page, title, '안내: <b>강조 아님</b> 그대로 보여야 함');
    const postUrl = page.url();

    const noJs = await browser.newContext({ javaScriptEnabled: false });
    try {
        const plain = await noJs.newPage();
        await plain.goto(postUrl);
        const body = plain.locator('#post-app .post-body');

        await expect(body.locator('b')).toHaveCount(0);
        await expect(body).toContainText('안내: <b>강조 아님</b> 그대로 보여야 함');
    } finally {
        await noJs.close();
    }
});

/**
 * Vue가 마운트하면 서버가 그린 본문을 통째로 교체한다. 같은 마크다운 파서를 쓰므로 교체
 * 전후 모양이 같아야 한다 — 전에는 서버가 원문을 그대로 찍었기 때문에(문법 문자 그대로)
 * Vue가 마운트되는 순간 눈에 띄게 다시 그려지는 모양이었다. "마운트되기 전"의 정확한
 * 순간은 페이지 로드 속도에 따라 달라져 JS를 켠 채로는 안정적으로 잡을 수 없으므로(경쟁
 * 상태), 그 경계는 위 no-JS 테스트가 이미 확인한다 — 여기서는 마운트가 끝난 뒤의 최종
 * 모양만 확인한다.
 */
test('Vue가 마운트된 뒤에도 서버와 같은 서식으로 보인다', async ({ page }) => {
    const title = uniqueTitle('SSR유지');
    await createOwnPost(page, title, '**굵게**와 *기울임*');
    const postUrl = page.url();

    await page.goto(postUrl);
    await expect(page.locator('[data-ssr-content]')).toHaveCount(0);
    const mountedBody = page.locator('#post-content-text');
    await expect(mountedBody.locator('strong')).toHaveText('굵게');
    await expect(mountedBody.locator('em')).toHaveText('기울임');
});
