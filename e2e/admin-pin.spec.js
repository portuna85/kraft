import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

/**
 * 목록 상단 고정은 관리자가 글마다 기한을 정해 한다(pinned_until). 공지(NOTICE)라고 자동으로 고정되지 않는다. user가 글을 쓰고, admin이 상세 화면에서 고정·해제한다.
 *
 * 기본 page를 관리자 상태로 쓰고 작성자는 openAs('user')로 연다 — 이 스펙에서 새로 로그인하지 않는다. 새 로그인(login 헬퍼)을 board.spec.js보다 앞에서 하면 이후 스펙들이 쓰는 저장된 로그인 상태가 풀리는 것이 관찰된다(서버가 아니라 Playwright 컨텍스트 쪽 현상이다).
 * 관리자 상태는 admin-fetch.spec.js도 같은 순서에서 그대로 쓴다.
 */
test.use({ storageState: storageStateFor('admin') });

test('관리자가 글을 고정하면 목록 상단에 나타나고, 해제하면 사라진다', async ({ page, openAs }) => {
    const title = uniqueTitle('고정');

    const authorPage = await openAs('user');
    await authorPage.goto('/posts/save');
    await authorPage.locator('#title').fill(title);
    await authorPage.locator('#content').fill('고정 시나리오용 본문입니다.');
    await authorPage.locator('#btn-save').click();
    await authorPage.waitForURL(/\/posts\/update\/\d+$/);
    const postUrl = authorPage.url();
    // 작성자에게는 고정 컨트롤이 없다.
    await expect(authorPage.locator('#post-pin-bar')).toHaveCount(0);
    await authorPage.close();

    // 고정하기 전에는 새 글이 고정 영역에 없다.
    await page.goto('/community');
    await expect(page.locator('.post-list--pinned .post-list__item').filter({ hasText: title })).toHaveCount(0);

    // 기한 입력은 지금부터 7일 뒤로 미리 채워져 있어 그대로 고정할 수 있다.
    await page.goto(postUrl);
    await expect(page.locator('#pin-until')).not.toHaveValue('');
    await page.locator('#btn-pin-post').click();
    await expect(page.locator('#post-pin-status')).toContainText('고정 중');
    await expect(page.locator('#btn-unpin-post')).toBeVisible();

    await page.goto('/community');
    await expect(page.locator('.post-list--pinned .post-list__item').filter({ hasText: title })).toHaveCount(1);

    // 해제하면 고정 영역에서 사라진다(본목록에는 그대로 있다).
    await page.goto(postUrl);
    await page.locator('#btn-unpin-post').click();
    await expect(page.locator('#post-pin-status')).toHaveCount(0);

    await page.goto('/community');
    await expect(page.locator('.post-list--pinned .post-list__item').filter({ hasText: title })).toHaveCount(0);
});

/** user가 글을 쓰고(선택적으로 댓글도 남기고) 그 글 주소를 돌려준다. 작성자 페이지는 닫는다. */
async function writePostAsUser(openAs, title, commentText = null) {
    const authorPage = await openAs('user');
    await authorPage.goto('/posts/save');
    await authorPage.locator('#title').fill(title);
    await authorPage.locator('#content').fill('숨김 시나리오용 본문입니다.');
    await authorPage.locator('#btn-save').click();
    await authorPage.waitForURL(/\/posts\/update\/\d+$/);
    if (commentText) {
        await authorPage.locator('#comment-content').fill(commentText);
        await authorPage.locator('#btn-comment-save').click();
        await expect(authorPage.locator('.comment-list__content')).toContainText(commentText);
    }
    const postUrl = authorPage.url();
    await authorPage.close();
    return postUrl;
}

test('관리자가 글을 숨기면 작성자에게는 숨김 안내가 보이고, 숨김을 풀면 다시 보인다', async ({ page, openAs }) => {
    const title = uniqueTitle('숨김');
    const postUrl = await writePostAsUser(openAs, title);

    await page.goto(postUrl);
    await page.locator('#btn-blind-post').click();
    // 관리자는 원문과 함께 숨김 표시·해제 버튼을 본다.
    await expect(page.locator('#post-admin-bar')).toContainText('관리자가 숨긴 글입니다');
    await expect(page.locator('#btn-unblind-post')).toBeVisible();

    const authorView = await openAs('user');
    await authorView.goto(postUrl);
    await expect(authorView.getByRole('heading', { name: '관리자가 숨긴 글입니다.' })).toBeVisible();
    await authorView.close();

    await page.locator('#btn-unblind-post').click();
    await expect(page.locator('#post-admin-bar')).toHaveCount(0);

    const restored = await openAs('user');
    await restored.goto(postUrl);
    await expect(restored.locator('#post-app')).toContainText(title);
    await restored.close();
});

test('관리자가 댓글을 숨기면 작성자에게는 가려지고, 숨김을 풀면 다시 보인다', async ({ page, openAs }) => {
    const comment = `숨김 댓글 ${Date.now()}`;
    const postUrl = await writePostAsUser(openAs, uniqueTitle('댓글숨김'), comment);
    const item = (p) => p.locator('.comment-list__item').filter({ hasText: comment });

    await page.goto(postUrl);
    await page.locator('.comment-list__item').filter({ hasText: comment }).locator('.btn-comment-blind').click();
    await expect(page.locator('.btn-comment-unblind')).toBeVisible();

    const authorView = await openAs('user');
    await authorView.goto(postUrl);
    await expect(authorView.locator('.comment-list')).toContainText('관리자가 숨긴 댓글입니다.');
    await expect(authorView.locator('.comment-list')).not.toContainText(comment);
    await authorView.close();

    await page.locator('.btn-comment-unblind').click();
    await expect(page.locator('.btn-comment-blind').first()).toBeVisible();

    const restored = await openAs('user');
    await restored.goto(postUrl);
    await expect(item(restored)).toContainText(comment);
    await restored.close();
});
