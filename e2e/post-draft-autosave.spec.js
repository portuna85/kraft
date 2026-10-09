import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * localStorage에 이 조각을 포함한 키가 있는지 확인한다. 키에는 회원 id가 들어가므로
 * (kraft:draft:{userId}:post-save) 접두사 대신 ':post-save'·
 * ':post-edit:'처럼 화면 종류를 가리키는 조각으로 찾는다.
 */
async function hasDraftKey(page, marker) {
    return page.evaluate(
        (m) => Object.keys(window.localStorage).some((key) => key.includes(m)),
        marker,
    );
}

async function createOwnPost(page, title) {
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('자동 임시 저장 테스트용 본문입니다.');
    await page.locator('#btn-save').click();
    // 등록 후 목록이 아니라 방금 쓴 글로 바로 이동한다.
    await page.waitForURL(/\/posts\/update\/\d+$/);
}

test.describe('글쓰기 — 자동 임시 저장', () => {
    test('입력하면 임시 저장되고, 새로고침하면 배너에서 복원할 수 있다', async ({ page }) => {
        const title = uniqueTitle('임시저장');
        await page.goto('/posts/save');

        await expect(page.locator('#draft-restore-banner')).toBeHidden();

        await page.locator('#title').fill(title);
        await page.locator('#content').fill('새로고침해도 되찾을 내용입니다.');
        await expect.poll(() => hasDraftKey(page, ':post-save')).toBe(true);

        await page.reload();

        const banner = page.locator('#draft-restore-banner');
        await expect(banner).toBeVisible();
        await expect(page.locator('#title')).toHaveValue('');

        await page.locator('#btn-draft-restore').click();
        await expect(banner).toBeHidden();
        await expect(page.locator('#title')).toHaveValue(title);
        await expect(page.locator('#content')).toHaveValue('새로고침해도 되찾을 내용입니다.');
    });

    test('배너에서 "새로 시작"을 누르면 빈 채로 남고 임시 저장도 지워진다', async ({ page }) => {
        await page.goto('/posts/save');
        await page.locator('#title').fill(uniqueTitle('버릴초안'));
        await page.locator('#content').fill('버려질 내용입니다.');
        await expect.poll(() => hasDraftKey(page, ':post-save')).toBe(true);

        await page.reload();
        await expect(page.locator('#draft-restore-banner')).toBeVisible();

        await page.locator('#btn-draft-discard').click();
        await expect(page.locator('#draft-restore-banner')).toBeHidden();
        await expect(page.locator('#title')).toHaveValue('');
        expect(await hasDraftKey(page, ':post-save')).toBe(false);
    });

    test('등록에 성공하면 임시 저장이 지워진다', async ({ page }) => {
        const title = uniqueTitle('등록후지움');
        await page.goto('/posts/save');
        await page.locator('#title').fill(title);
        await page.locator('#content').fill('등록되면 임시 저장이 남지 않아야 합니다.');
        await expect.poll(() => hasDraftKey(page, ':post-save')).toBe(true);

        await page.locator('#btn-save').click();
        await page.waitForURL(/\/posts\/update\/\d+$/);

        await page.goto('/posts/save');
        expect(await hasDraftKey(page, ':post-save')).toBe(false);
        await expect(page.locator('#draft-restore-banner')).toBeHidden();
    });

    test('아무것도 쓰지 않은 빈 화면에서는 배너가 뜨지 않는다', async ({ page }) => {
        await page.goto('/posts/save');
        await page.reload();
        await expect(page.locator('#draft-restore-banner')).toBeHidden();
    });
});

test.describe('편집 — 자동 임시 저장', () => {
    test('편집 중 바꾼 내용이 새로고침 후에도 배너로 복원된다', async ({ page }) => {
        const title = uniqueTitle('편집임시저장');
        await createOwnPost(page, title);

        await page.locator('#btn-edit').click();
        await page.locator('#content').fill('편집 중 새로고침 전에 잃을 뻔한 내용입니다.');
        await expect.poll(() => hasDraftKey(page, ':post-edit:')).toBe(true);

        // 새로고침하면 조회 모드로 서버가 다시 그려준다 — 저장하지 않았으므로 아직 원래 본문이다.
        await page.reload();
        await expect(page.locator('#post-content-text')).not.toContainText('새로고침 전에 잃을 뻔한');

        await page.locator('#btn-edit').click();
        const banner = page.locator('#draft-restore-banner');
        await expect(banner).toBeVisible();

        await page.locator('#btn-draft-restore').click();
        await expect(page.locator('#content')).toHaveValue('편집 중 새로고침 전에 잃을 뻔한 내용입니다.');
    });

    test('저장에 성공하면 임시 저장이 지워진다', async ({ page }) => {
        const title = uniqueTitle('편집저장후지움');
        await createOwnPost(page, title);

        await page.locator('#btn-edit').click();
        await page.locator('#content').fill('저장하면 임시 저장이 남지 않아야 합니다.');
        await expect.poll(() => hasDraftKey(page, ':post-edit:')).toBe(true);

        await page.locator('#btn-update').click();
        // 저장 후 이동은 편집 중이던 바로 그 URL로 돌아간다이라
        // waitForURL은 URL이 안 바뀌므로 곧바로(저장이 끝나기 전에) 통과해 버린다 — 실제로
        // 새로고침이 끝났다는 신호(플래시 메시지, 자동 재시도되는 assertion)로 기다린다.
        await expect(page.locator('#flash')).toContainText('글이 수정되었습니다.');

        expect(await hasDraftKey(page, ':post-edit:')).toBe(false);
    });

    test('취소하면 임시 저장도 함께 지워진다', async ({ page }) => {
        const title = uniqueTitle('편집취소후지움');
        await createOwnPost(page, title);

        await page.locator('#btn-edit').click();
        await page.locator('#content').fill('취소할 내용입니다.');
        await expect.poll(() => hasDraftKey(page, ':post-edit:')).toBe(true);

        page.once('dialog', (dialog) => dialog.accept());
        await page.locator('#btn-cancel-edit').click();

        expect(await hasDraftKey(page, ':post-edit:')).toBe(false);
    });

    test('원본과 같은 내용의 초안은 배너를 띄우지 않는다', async ({ page }) => {
        const title = uniqueTitle('원본과동일');
        const content = '자동 임시 저장 테스트용 본문입니다.'; // createOwnPost가 쓰는 본문과 맞춘다.
        await createOwnPost(page, title);

        // 키에는 회원 id가 들어간다 — 직접 조립하지 않고, 실제
        // 편집 흐름을 한 번 거쳐 이 화면이 실제로 쓰는 키를 알아낸다.
        await page.locator('#btn-edit').click();
        await page.locator('#content').fill('키를 알아내기 위한 임시 변경입니다.');
        // 디바운스(800ms) 뒤 저장될 때까지 고정 대기 없이 기다린다.
        await expect.poll(() => hasDraftKey(page, ':post-edit:'), { message: '편집 초안 키를 찾아야 한다' }).toBe(true);
        const draftKey = await page.evaluate(() =>
            Object.keys(window.localStorage).find((key) => key.includes(':post-edit:')));

        // 서버 원본과 완전히 같은 초안을 그 키에 덮어 심는다 — checkAvailable의 "무의미한
        // 초안" 판단(원본과 같으면 배너를 띄우지 않는다)을 실제로 겨냥한다.
        await page.evaluate(
            ({ key, t, c }) => {
                window.localStorage.setItem(
                    key,
                    JSON.stringify({ savedAt: Date.now(), title: t, content: c, category: 'FREE' }),
                );
            },
            { key: draftKey, t: title, c: content },
        );

        await page.reload();
        await page.locator('#btn-edit').click();
        await expect(page.locator('#draft-restore-banner')).toBeHidden();
    });
});
