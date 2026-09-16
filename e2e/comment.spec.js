import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

async function openOwnPost(page) {
    const title = uniqueTitle('댓글');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('댓글 테스트용 글입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
}

test('빈 댓글은 브라우저 검증이 막고 요청 자체가 나가지 않는다', async ({ page }) => {
    await openOwnPost(page);

    let requested = false;
    page.on('request', (request) => {
        if (request.method() === 'POST' && /\/api\/v1\/posts\/\d+\/comments$/.test(request.url())) {
            requested = true;
        }
    });

    await page.locator('#btn-comment-save').click();

    expect(requested, 'required가 제출 자체를 막는다').toBe(false);
    await expect(page.locator('.comment-list__content')).toHaveCount(0);
});

test('댓글을 등록하면 목록에 나타난다', async ({ page }) => {
    await openOwnPost(page);

    await page.locator('#comment-content').fill('E2E가 남긴 댓글입니다.');
    await page.locator('#btn-comment-save').click();

    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    await expect(page.locator('.comment-list__content')).toContainText('E2E가 남긴 댓글입니다.');
});

/**
 * 댓글 목록의 수정·삭제 버튼은 document에 붙은 위임 핸들러로 동작한다. 위임이 깨지면 버튼이
 * 아무 반응도 하지 않고 조용히 죽으므로, 실제로 눌러 보는 것 말고는 확인할 방법이 없다.
 */
test('댓글을 인라인으로 수정할 수 있다 (위임 핸들러)', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('수정 전 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('수정 전 댓글');

    await page.locator('.btn-comment-edit').first().click();

    const editForm = page.locator('.comment-edit-form').first();
    await expect(editForm).toBeVisible();
    await editForm.locator('textarea').fill('수정 후 댓글');
    await editForm.getByRole('button', { name: '저장' }).click();

    await expect(page.locator('#flash')).toContainText('댓글이 수정되었습니다.');
    await expect(page.locator('.comment-list__content')).toContainText('수정 후 댓글');
});

test('댓글 수정을 취소하면 읽기 상태로 돌아간다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('취소할 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('취소할 댓글');

    await page.locator('.btn-comment-edit').first().click();
    await expect(page.locator('.comment-edit-form').first()).toBeVisible();

    await page.locator('.btn-comment-cancel').first().click();

    await expect(page.locator('.comment-edit-form').first()).toBeHidden();
    await expect(page.locator('.comment-list__content')).toContainText('취소할 댓글');
});

/**
 * 삭제 확인 모달은 게시글과 댓글이 함께 쓴다. 모달이 닫히면 원래 눌렀던 버튼으로 포커스를
 * 되돌리는데, 이것이 깨지면 키보드 사용자가 목록 맨 위로 튕긴다.
 */
/**
 * 취소 버튼을 저장 요청이 끝나기 전에 누를 수 있으면, 화면은 취소로 보이는데 나중에
 * 도착한 응답이 그 내용을 되돌려 놓는 경쟁이 생긴다(개선 보고서 "저장 중 댓글 변경과
 * 동적 삭제 모듈 누락").
 */
test('댓글 저장 중에는 취소할 수 없고, 응답이 오면 저장한 내용이 반영된다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('원본 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('원본 댓글');

    await page.locator('.btn-comment-edit').first().click();
    const editForm = page.locator('.comment-edit-form').first();
    await editForm.locator('textarea').fill('저장 시도 중인 내용');

    let releaseSave;
    const gate = new Promise((resolve) => {
        releaseSave = resolve;
    });
    await page.route(/\/api\/v1\/comments\/\d+$/, async (route) => {
        await gate;
        await route.continue();
    });

    await editForm.getByRole('button', { name: '저장' }).click();

    // 저장 요청이 진행 중인 동안 취소 버튼·입력창이 비활성화되어야 한다.
    await expect(page.locator('.btn-comment-cancel').first()).toBeDisabled();
    await expect(editForm.locator('textarea')).toBeDisabled();

    releaseSave();
    await expect(page.locator('#flash')).toContainText('댓글이 수정되었습니다.');
    await expect(page.locator('.comment-list__content')).toContainText('저장 시도 중인 내용');
});

/**
 * 남의 글에 처음 남기는 댓글은 최초 DOM에 게시글 삭제 버튼도, 기존 댓글도 없다. 삭제 확인
 * 모달 모듈이 그 최초 상태만 보고 로드 여부를 정하면, 방금 낙관적으로 추가된 이 댓글의
 * 삭제 버튼은 위임 핸들러가 없어 눌러도 반응이 없었다.
 */
test('남의 글에 처음 남긴 댓글도 곧바로 지울 수 있다', async ({ page, browser }) => {
    await openOwnPost(page);
    const postUrl = page.url();

    const otherContext = await browser.newContext({ storageState: storageStateFor('other') });
    const otherPage = await otherContext.newPage();
    try {
        await otherPage.goto(postUrl);
        // 남의 글이므로 게시글 삭제 버튼은 없다 — 이 시나리오가 재현하려는 전제 조건이다.
        await expect(otherPage.locator('#btn-delete-post')).toHaveCount(0);

        await otherPage.locator('#comment-content').fill('처음 남기는 댓글입니다.');
        await otherPage.locator('#btn-comment-save').click();
        await expect(otherPage.locator('.comment-list__content')).toContainText('처음 남기는 댓글입니다.');

        await otherPage.locator('.btn-comment-delete').first().click();
        const modal = otherPage.locator('#confirmDeleteModal');
        await expect(modal).toBeVisible();
        await otherPage.locator('#btn-confirm-delete').click();

        await expect(otherPage.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
        await expect(otherPage.locator('.comment-list__content')).toHaveCount(0);
    } finally {
        await otherContext.close();
    }
});

test('댓글 삭제: 취소하면 그대로, 확인하면 지워진다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('지울 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('지울 댓글');

    await page.locator('.btn-comment-delete').first().click();
    const modal = page.locator('#confirmDeleteModal');
    await expect(modal).toBeVisible();
    await expect(page.locator('#confirmDeleteModalLabel')).toContainText('댓글 삭제');

    await page.locator('#btn-cancel-delete').click();
    await expect(modal).toBeHidden();
    await expect(page.locator('.comment-list__content')).toContainText('지울 댓글');

    // 모달을 닫으면 호출한 버튼으로 포커스가 돌아와야 한다.
    await expect(page.locator('.btn-comment-delete').first()).toBeFocused();

    await page.locator('.btn-comment-delete').first().click();
    await expect(modal).toBeVisible();
    await page.locator('#btn-confirm-delete').click();

    await expect(page.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
    await expect(page.locator('.comment-list__content')).toHaveCount(0);
});
