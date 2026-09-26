import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

// useDraftAutosave.js의 디바운스(800ms)를 여유 있게 기다린다.
const AUTOSAVE_DEBOUNCE_WAIT = 1100;

/** localStorage에서 이 접두사로 시작하는 키가 있는지 확인한다. */
async function hasDraftKey(page, prefix) {
    return page.evaluate(
        (p) => Object.keys(window.localStorage).some((key) => key.startsWith(p)),
        prefix,
    );
}

async function createOwnPost(page, title) {
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('자동 임시 저장 테스트용 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
}

test.describe('글쓰기 — 자동 임시 저장', () => {
    test('입력하면 임시 저장되고, 새로고침하면 배너에서 복원할 수 있다', async ({ page }) => {
        const title = uniqueTitle('임시저장');
        await page.goto('/posts/save');

        await expect(page.locator('#draft-restore-banner')).toBeHidden();

        await page.locator('#title').fill(title);
        await page.locator('#content').fill('새로고침해도 되찾을 내용입니다.');
        await page.waitForTimeout(AUTOSAVE_DEBOUNCE_WAIT);

        expect(await hasDraftKey(page, 'kraft:draft:post-save')).toBe(true);

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
        await page.waitForTimeout(AUTOSAVE_DEBOUNCE_WAIT);

        await page.reload();
        await expect(page.locator('#draft-restore-banner')).toBeVisible();

        await page.locator('#btn-draft-discard').click();
        await expect(page.locator('#draft-restore-banner')).toBeHidden();
        await expect(page.locator('#title')).toHaveValue('');
        expect(await hasDraftKey(page, 'kraft:draft:post-save')).toBe(false);
    });

    test('등록에 성공하면 임시 저장이 지워진다', async ({ page }) => {
        const title = uniqueTitle('등록후지움');
        await page.goto('/posts/save');
        await page.locator('#title').fill(title);
        await page.locator('#content').fill('등록되면 임시 저장이 남지 않아야 합니다.');
        await page.waitForTimeout(AUTOSAVE_DEBOUNCE_WAIT);
        expect(await hasDraftKey(page, 'kraft:draft:post-save')).toBe(true);

        await page.locator('#btn-save').click();
        await page.waitForURL('/');

        await page.goto('/posts/save');
        expect(await hasDraftKey(page, 'kraft:draft:post-save')).toBe(false);
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
        await page.waitForTimeout(AUTOSAVE_DEBOUNCE_WAIT);

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
        await page.waitForTimeout(AUTOSAVE_DEBOUNCE_WAIT);
        expect(await hasDraftKey(page, 'kraft:draft:post-edit:')).toBe(true);

        await page.locator('#btn-update').click();
        await page.waitForURL('/');

        expect(await hasDraftKey(page, 'kraft:draft:post-edit:')).toBe(false);
    });

    test('취소하면 임시 저장도 함께 지워진다', async ({ page }) => {
        const title = uniqueTitle('편집취소후지움');
        await createOwnPost(page, title);

        await page.locator('#btn-edit').click();
        await page.locator('#content').fill('취소할 내용입니다.');
        await page.waitForTimeout(AUTOSAVE_DEBOUNCE_WAIT);
        expect(await hasDraftKey(page, 'kraft:draft:post-edit:')).toBe(true);

        page.once('dialog', (dialog) => dialog.accept());
        await page.locator('#btn-cancel-edit').click();

        expect(await hasDraftKey(page, 'kraft:draft:post-edit:')).toBe(false);
    });

    test('원본과 같은 내용의 초안은 배너를 띄우지 않는다', async ({ page }) => {
        const title = uniqueTitle('원본과동일');
        const content = '자동 임시 저장 테스트용 본문입니다.'; // createOwnPost가 쓰는 본문과 맞춘다.
        await createOwnPost(page, title);

        const postId = page.url().match(/\/posts\/update\/(\d+)$/)?.[1];
        expect(postId, '게시글 id를 URL에서 얻어야 한다').toBeTruthy();

        // 서버 원본과 완전히 같은 초안을 직접 심어 둔다 — checkAvailable의 "무의미한 초안"
        // 판단(원본과 같으면 배너를 띄우지 않는다)을 실제로 겨냥한다.
        await page.evaluate(
            ({ id, t, c }) => {
                window.localStorage.setItem(
                    `kraft:draft:post-edit:${id}`,
                    JSON.stringify({ savedAt: Date.now(), title: t, content: c, category: 'FREE' }),
                );
            },
            { id: postId, t: title, c: content },
        );

        await page.reload();
        await page.locator('#btn-edit').click();
        await expect(page.locator('#draft-restore-banner')).toBeHidden();
    });
});
