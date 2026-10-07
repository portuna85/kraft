import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

/**
 * 게시글은 지워도 곧바로 사라지지 않고 소프트 삭제된다. 일반 사용자에게는 404지만 관리자는 열어 볼 수
 * 있고, 복구하면 다시 보통 글로 돌아온다. 두 역할이 필요하므로 user가 쓰고 지운 뒤 admin이 복구한다.
 */
test.use({ storageState: storageStateFor('user') });

test('삭제한 글은 관리자만 열 수 있고, 복구하면 목록과 수정 버튼이 돌아온다', async ({ page, openAs }) => {
    const title = uniqueTitle('복구');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('복구 시나리오용 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL(/\/posts\/update\/\d+$/);
    const postUrl = page.url();

    await page.locator('#btn-delete-post').click();
    await page.locator('#btn-confirm-delete').click();
    await page.waitForURL('/community');

    // 작성자도 더는 열 수 없다.
    await page.goto(postUrl);
    await expect(page.locator('.page-title')).toContainText('게시글을 찾을 수 없습니다');

    const adminPage = await openAs('admin');
    await adminPage.goto(postUrl);
    await expect(adminPage.locator('#post-title-text')).toHaveText(title);
    await expect(adminPage.locator('#post-admin-bar')).toContainText('삭제된 글입니다');
    // 삭제된 글에는 추천·신고·수정·삭제를 두지 않는다.
    await expect(adminPage.locator('#btn-like')).toHaveCount(0);
    await expect(adminPage.locator('#btn-edit')).toHaveCount(0);
    await expect(adminPage.locator('#btn-delete-post')).toHaveCount(0);

    await adminPage.locator('#btn-restore-post').click();
    // 복구하면 화면을 다시 불러 보통 글로 그린다.
    await expect(adminPage.locator('#post-admin-bar')).toHaveCount(0);
    await expect(adminPage.locator('#btn-delete-post')).toHaveCount(1);

    // 작성자는 다시 열 수 있고 목록에도 돌아온다.
    await page.goto(postUrl);
    await expect(page.locator('#post-title-text')).toHaveText(title);
    await page.goto('/community');
    await expect(page.getByRole('link', { name: title, exact: true })).toBeVisible();
    await adminPage.close();
});
