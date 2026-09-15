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
