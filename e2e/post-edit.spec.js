import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/** 내 글을 하나 만들고 그 상세 화면으로 간다. */
async function createOwnPost(page, title) {
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('편집 테스트용 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
}

test('F12 회귀 방지: 분류만 바꾸고 취소하면 확인을 묻고 분류가 되돌아온다', async ({ page }) => {
    await createOwnPost(page, uniqueTitle('편집'));

    await page.locator('#btn-edit').click();
    await expect(page.locator('#edit-category')).toHaveValue('FREE');

    await page.locator('#edit-category').selectOption('QNA');

    // 예전에는 분류가 변경 감지에서 빠져 있어 확인창 없이 그냥 닫혔고, 바뀐 분류가 남아
    // 다음 저장에 딸려 들어갔다.
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

    await page.waitForURL('/');
    await expect(page.locator('#flash')).toContainText('글이 수정되었습니다.');
    await openPostByTitle(page, newTitle);
    await expect(page.locator('#post-title-text')).toHaveText(newTitle);
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

test('다른 사람의 글에는 수정·삭제 버튼이 보이지 않는다', async ({ page }) => {
    await openPostByTitle(page, '다른 사람의 글');

    await expect(page.locator('#btn-edit')).toHaveCount(0);
    await expect(page.locator('#btn-delete-post')).toHaveCount(0);
});
