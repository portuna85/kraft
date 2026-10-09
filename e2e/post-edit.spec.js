import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/** 내 글을 하나 만들고 그 상세 화면으로 간다. */
async function createOwnPost(page, title) {
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('편집 테스트용 본문입니다.');
    await page.locator('#btn-save').click();
    // 등록 후 목록이 아니라 방금 쓴 글로 바로 이동한다.
    await page.waitForURL(/\/posts\/update\/\d+$/);
}

/**
 * navigator.share가 있으면 그 시트를 먼저 띄우고, 없으면 클립보드 복사로 물러선다. Playwright의 chromium 프로젝트(channel: 'chromium', 전체 데스크톱 빌드)는 navigator.share를 이미 구현하고 있으므로,
 * "없는 브라우저" 경로를 보려면 명시적으로 지워야 한다 — 지우지 않으면(사용자 제스처 밖 자동화 호출이라) 거절되어 클립보드 대신 실패 토스트가 뜬다.
 */
test.describe('공유 버튼', () => {
    test('navigator.share가 없으면 클립보드로 복사한다', async ({ page, context }) => {
        await context.grantPermissions(['clipboard-read', 'clipboard-write']);
        await page.addInitScript(() => {
            navigator.share = undefined;
        });
        await createOwnPost(page, uniqueTitle('공유'));

        await page.locator('#btn-share').click();

        await expect(page.locator('#app-toast-body')).toContainText('링크를 복사했습니다.');
        const copied = await page.evaluate(() => navigator.clipboard.readText());
        expect(copied).toContain('/posts/update/');
    });

    test('navigator.share가 있으면 그 시트를 먼저 띄우고 클립보드는 건드리지 않는다', async ({ page, context }) => {
        await context.grantPermissions(['clipboard-read', 'clipboard-write']);
        await page.addInitScript(() => {
            window.__shareCalls = [];
            navigator.share = (data) => {
                window.__shareCalls.push(data);
                return Promise.resolve();
            };
        });
        await createOwnPost(page, uniqueTitle('공유'));
        // 클립보드를 미리 알아볼 수 있는 값으로 채워, 공유 시트 경로에서는 이 값이 그대로 남는지(=클립보드에 쓰지 않았는지) 확인한다. 페이지가 뜬 뒤에 써야 한다 — 탐색 전(about:blank)에는 Clipboard API 자체가 없다.
        await page.evaluate(() => navigator.clipboard.writeText('untouched'));

        await page.locator('#btn-share').click();

        const calls = await page.evaluate(() => window.__shareCalls);
        expect(calls).toHaveLength(1);
        expect(calls[0].url).toContain('/posts/update/');
        expect(calls[0].title).toBeTruthy();
        expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('untouched');
    });

    test('공유 시트를 취소해도(AbortError) 클립보드로 대신 복사하지 않는다', async ({ page, context }) => {
        await context.grantPermissions(['clipboard-read', 'clipboard-write']);
        await page.addInitScript(() => {
            navigator.share = () => Promise.reject(new DOMException('취소됨', 'AbortError'));
        });
        await createOwnPost(page, uniqueTitle('공유'));
        await page.evaluate(() => navigator.clipboard.writeText('untouched'));

        await page.locator('#btn-share').click();
        // 성공도 실패도 토스트가 뜨지 않는다 — 취소는 조용히 끝난다.
        await expect(page.locator('#app-toast')).toBeHidden();
        expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('untouched');
    });
});

test('회귀 방지: 분류만 바꾸고 취소하면 확인을 묻고 분류가 되돌아온다', async ({ page }) => {
    await createOwnPost(page, uniqueTitle('편집'));

    await page.locator('#btn-edit').click();
    await expect(page.locator('#edit-category')).toHaveValue('FREE');

    await page.locator('#edit-category').selectOption('QNA');

    // 분류가 변경 감지에 포함되어야 한다 — 빠지면 확인창 없이 그냥 닫혀 바뀐 분류가 남아 다음 저장에 딸려 들어간다.
    let dialogMessage = null;
    page.once('dialog', (dialog) => {
        dialogMessage = dialog.message();
        dialog.accept();
    });
    await page.locator('#btn-cancel-edit').click();

    expect(dialogMessage, '변경 사항이 있으므로 확인을 물어야 한다').toContain('버리시겠습니까');

    await page.locator('#btn-edit').click();
    await expect(page.locator('#edit-category')).toHaveValue('FREE');
});

test('제목·본문·분류를 바꿔 저장하면 반영된다', async ({ page }) => {
    const title = uniqueTitle('편집');
    await createOwnPost(page, title);
    const newTitle = `${title}-수정됨`;

    await page.locator('#btn-edit').click();
    await page.locator('#title').fill(newTitle);
    await page.locator('#content').fill('수정된 본문입니다.');
    await page.locator('#edit-category').selectOption('QNA');
    await page.locator('#btn-update').click();

    // 목록으로 튕기지 않고 같은 글을 새로고침해서 보여준다.
    await page.waitForURL(/\/posts\/update\/\d+$/);
    await expect(page.locator('#flash')).toContainText('글이 수정되었습니다.');
    await expect(page.locator('#post-title-text')).toHaveText(newTitle);
});

/** 수정 요청은 "이 버전을 기준으로 고친다"를 If-Match 헤더로만 밝힌다. 본문에는 version이 없다. */
test('저장 요청이 편집을 시작할 때의 글 버전을 If-Match로 보낸다', async ({ page }) => {
    await createOwnPost(page, uniqueTitle('조건부'));

    const putRequest = page.waitForRequest((r) => r.method() === 'PUT' && /\/api\/v1\/posts\/\d+$/.test(r.url()));
    await page.locator('#btn-edit').click();
    await page.locator('#content').fill('If-Match 확인용 본문입니다.');
    await page.locator('#btn-update').click();

    const request = await putRequest;
    expect(request.headers()['if-match']).toMatch(/^"\d+"$/);
    expect(request.postDataJSON()).not.toHaveProperty('version');
    await page.waitForURL(/\/posts\/update\/\d+$/);
    await expect(page.locator('#flash')).toContainText('글이 수정되었습니다.');
});

/** 편집을 시작한 뒤 다른 곳(다른 탭·기기)에서 먼저 저장하면, 나중 저장이 말없이 덮어쓰지 않고 412로 거절돼 충돌 안내가 뜬다. 거절된 요청은 서버의 글을 바꾸지 않는다. */
test('편집을 시작한 뒤 다른 곳에서 먼저 저장되면 충돌 안내가 뜨고 덮어쓰지 않는다', async ({ page }) => {
    const title = uniqueTitle('충돌');
    await createOwnPost(page, title);
    const postId = page.url().match(/\/posts\/update\/(\d+)$/)[1];

    await page.locator('#btn-edit').click();

    // 다른 곳에서의 저장을 흉내 낸다 — 현재 버전(ETag)을 읽어 그 기준으로 먼저 고친다.
    const otherSave = await page.evaluate(async (id) => {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const header = document.querySelector('meta[name="_csrf_header"]').content;
        const current = await fetch(`/api/v1/posts/${id}`);
        const etag = current.headers.get('ETag');
        const res = await fetch(`/api/v1/posts/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'If-Match': etag, [header]: token },
            body: JSON.stringify({ title: '다른 곳에서 먼저 수정', content: '먼저 저장된 본문' }),
        });
        return { status: res.status, etag: res.headers.get('ETag'), previous: etag };
    }, postId);
    expect(otherSave.status).toBe(200);
    // 저장하면 ETag(버전)가 바뀐다.
    expect(otherSave.etag).not.toBe(otherSave.previous);

    await page.locator('#content').fill('이 화면이 늦게 저장하려는 본문');
    await page.locator('#btn-update').click();

    // 저장 실패는 화면 상단 알림(#flash)에 서버가 준 문구 그대로 보인다.
    await expect(page.locator('#flash')).toContainText('다른 곳에서 이미 수정된 글입니다');
    // 저장이 막혔을 뿐 화면은 편집 상태로 남아 다시 시도할 수 있다.
    await expect(page.locator('#btn-update')).toBeEnabled();

    const stored = await page.evaluate(async (id) => (await fetch(`/api/v1/posts/${id}`)).json(), postId);
    expect(stored.title).toBe('다른 곳에서 먼저 수정');
    expect(stored.content).toBe('먼저 저장된 본문');
});

test('바꾼 것이 없으면 취소할 때 묻지 않는다', async ({ page }) => {
    await createOwnPost(page, uniqueTitle('편집'));

    let asked = false;
    page.on('dialog', (dialog) => {
        asked = true;
        dialog.accept();
    });

    await page.locator('#btn-edit').click();
    await page.locator('#btn-cancel-edit').click();

    await expect(page.locator('#post-view')).toBeVisible();
    expect(asked).toBe(false);
});

test('저장 중에는 취소·제목·본문·분류가 모두 비활성 상태다', async ({ page }) => {
    const title = uniqueTitle('편집');
    await createOwnPost(page, title);

    let releaseResponse;
    const held = new Promise((resolve) => {
        releaseResponse = resolve;
    });
    await page.route('**/api/v1/posts/**', async (route) => {
        if (route.request().method() !== 'PUT') {
            await route.continue();
            return;
        }
        await held;
        await route.continue();
    });

    await page.locator('#btn-edit').click();
    await page.locator('#content').fill('저장 지연 중 상태 확인용 본문입니다.');
    await page.locator('#btn-update').click();

    // 응답이 지연되는 동안 취소·제목·본문·분류가 전부 비활성 상태여야 한다 — 아니면 저장 진행 중 취소해 view로 돌아간 뒤 지연 응답이 이미 사라진 폼에 오류를 표시하는 경쟁이 생긴다.
    await expect(page.locator('#btn-cancel-edit')).toBeDisabled();
    await expect(page.locator('#title')).toBeDisabled();
    await expect(page.locator('#content')).toBeDisabled();
    await expect(page.locator('#edit-category')).toBeDisabled();
    await expect(page.locator('#btn-update')).toBeDisabled();

    releaseResponse();
    await page.waitForURL(/\/posts\/update\/\d+$/);
    await expect(page.locator('#flash')).toContainText('글이 수정되었습니다.');
});

test('저장이 실패한 뒤에는 다시 시도할 수 있다', async ({ page }) => {
    const title = uniqueTitle('편집');
    await createOwnPost(page, title);

    let failOnce = true;
    await page.route('**/api/v1/posts/**', async (route) => {
        if (route.request().method() !== 'PUT' || !failOnce) {
            await route.continue();
            return;
        }
        failOnce = false;
        await route.fulfill({ status: 500, contentType: 'application/json', body: '{"detail":"실패"}' });
    });

    await page.locator('#btn-edit').click();
    await page.locator('#content').fill('재시도 테스트용 본문입니다.');
    await page.locator('#btn-update').click();

    await expect(page.locator('#btn-update')).toBeEnabled();
    await expect(page.locator('#title')).toBeEnabled();

    await page.locator('#btn-update').click();
    await page.waitForURL(/\/posts\/update\/\d+$/);
    await expect(page.locator('#flash')).toContainText('글이 수정되었습니다.');
});

test('다른 사람의 글에는 수정·삭제 버튼이 보이지 않는다', async ({ page }) => {
    await openPostByTitle(page, '다른 사람의 글');

    await expect(page.locator('#btn-edit')).toHaveCount(0);
    await expect(page.locator('#btn-delete-post')).toHaveCount(0);
});

/** 본문은 서버가 먼저 HTML로 그린다. JS 없이도 읽을 수 있어야 하고, Vue가 마운트한 뒤에는 그 내용을 교체해 제목·본문이 한 번만 보여야 하며, 편집도 그대로 된다. */
test('JS 없이도 본문을 읽을 수 있고, 마운트 뒤에는 한 번만 보이며 편집이 된다', async ({ page, browser }) => {
    const title = uniqueTitle('서버렌더');
    await createOwnPost(page, title);
    const postUrl = page.url();

    const noJs = await browser.newContext({ storageState: storageStateFor('user'), javaScriptEnabled: false });
    try {
        const plain = await noJs.newPage();
        await plain.goto(postUrl);
        await expect(plain.locator('#post-app h1')).toHaveText(title);
        await expect(plain.locator('#post-app .post-body')).toHaveText('편집 테스트용 본문입니다.');
    } finally {
        await noJs.close();
    }

    await expect(page.locator('#post-title-text')).toHaveText(title);
    await expect(page.locator('[data-ssr-content]')).toHaveCount(0);
    await expect(page.locator('#post-app h1')).toHaveCount(1);
    // 마운트 뒤 읽기 화면에 정확히 한 번 보인다. page.getByText(...)로 페이지 전체를 뒤지지 않는 이유: 편집 폼(MarkdownToolbar.vue의 textarea)이 같은 내용으로 미리 채워진 채 DOM에 항상 남아 있다(required 검증을 유지하려는 의도 — MarkdownToolbar.vue 주석 참고).
    await expect(page.locator('#post-content-text')).toHaveText('편집 테스트용 본문입니다.');

    await page.locator('#btn-edit').click();
    await expect(page.locator('#edit-category')).toBeVisible();
});

/** 세션 만료를 막기 위해 10분마다 가벼운 GET(/api/v1/users/me/ping)을 보낸다. page.clock으로 실제 10분을 기다리지 않고 타이머만 앞으로 돌린다 — setInterval이 실제로 등록됐는지, 주기가 맞는지를 확인하는 것이 목적이고 네트워크 자체는 그대로 나간다. */
test('편집 화면이 열려 있는 동안 세션 연장 핑을 주기적으로 보낸다', async ({ page }) => {
    await page.clock.install();

    const pingRequests = [];
    page.on('request', (request) => {
        if (request.url().includes('/api/v1/users/me/ping')) {
            pingRequests.push(request);
        }
    });

    await createOwnPost(page, uniqueTitle('세션연장'));
    expect(pingRequests).toHaveLength(0);

    // 입력이 없으면 방치된 화면으로 보고 핑하지 않는다.
    await page.clock.fastForward('10:00');
    await page.waitForTimeout(300);
    expect(pingRequests).toHaveLength(0);

    // 입력이 있었고 탭이 보이면 다음 주기에 핑한다.
    await page.keyboard.press('Shift');
    await page.clock.fastForward('10:00');
    await expect.poll(() => pingRequests.length).toBe(1);

    // 핑한 뒤 새 입력이 없으면 다음 주기는 건너뛴다.
    await page.clock.fastForward('10:00');
    await page.waitForTimeout(300);
    expect(pingRequests).toHaveLength(1);

    await page.keyboard.press('Shift');
    await page.clock.fastForward('10:00');
    await expect.poll(() => pingRequests.length).toBe(2);
});
