import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

async function createOwnPost(page, title) {
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('추천·삭제 테스트용 글입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
}

/**
 * 추천은 "뒤집어라"가 아니라 "이 상태로 만들어라"를 보낸다(F10). 화면은 낙관적으로 미리
 * 바꾸지 않고 서버가 돌려준 최종 상태만 반영한다.
 */
test('추천을 눌렀다 다시 누르면 원래대로 돌아온다', async ({ page }) => {
    await createOwnPost(page, uniqueTitle('추천'));

    const button = page.locator('#btn-like');
    const count = page.locator('#like-count');
    await expect(count).toHaveText('0');
    await expect(button).toHaveAttribute('aria-pressed', 'false');

    await button.click();
    await expect(count).toHaveText('1');
    await expect(button).toHaveAttribute('aria-pressed', 'true');
    await expect(button).toHaveClass(/is-active/);

    // 편집 모드 전환으로 컴포넌트가 다시 렌더링돼도 추천 상태가 유지된다.
    await page.locator('#btn-edit').click();
    await page.locator('#btn-cancel-edit').click();
    await expect(count).toHaveText('1');
    await expect(button).toHaveAttribute('aria-pressed', 'true');

    await button.click();
    await expect(count).toHaveText('0');
    await expect(button).toHaveAttribute('aria-pressed', 'false');
});

test('같은 상태를 다시 요청해도 결과가 같다 (멱등)', async ({ page }) => {
    await createOwnPost(page, uniqueTitle('추천멱등'));

    await page.locator('#btn-like').click();
    await expect(page.locator('#like-count')).toHaveText('1');

    // 화면을 새로 열어 같은 "추천함" 상태를 한 번 더 보낸다.
    await page.reload();
    await page.evaluate(async () => {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const header = document.querySelector('meta[name="_csrf_header"]').content;
        const id = document.querySelector('#id').value;
        await fetch(`/api/v1/posts/${id}/like`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', [header]: token },
            body: JSON.stringify({ liked: true }),
        });
    });

    await page.reload();
    await expect(page.locator('#like-count')).toHaveText('1');
});

test('게시글 삭제: 취소하면 그대로, 확인하면 목록으로 돌아간다', async ({ page }) => {
    const title = uniqueTitle('삭제');
    await createOwnPost(page, title);

    await page.locator('#btn-delete-post').click();
    const modal = page.locator('#confirmDeleteModal');
    await expect(modal).toBeVisible();
    await expect(page.locator('#confirmDeleteModalLabel')).toContainText('게시글 삭제');
    // 삭제 확인 문구에 대상을 명시한다(문서 5.2) — 어느 글을 지우는지 모달 안에서 알 수 있다.
    await expect(page.locator('#confirmDeleteMessage')).toContainText(title);

    await page.locator('#btn-cancel-delete').click();
    await expect(modal).toBeHidden();
    await expect(page.locator('#post-title-text')).toHaveText(title);

    await page.locator('#btn-delete-post').click();
    await page.locator('#btn-confirm-delete').click();

    await page.waitForURL('/');
    await expect(page.locator('#flash')).toContainText('글이 삭제되었습니다.');
});

/**
 * 문서 5.5: 찾을 수 없는 페이지는 상황 설명과 게시판 복귀 링크를 제공해야 한다.
 * 예전에는 설명 문구만 있고 복귀 링크가 없었다.
 */
test('삭제된 글 주소로 들어가면 설명과 게시판 복귀 링크가 있는 404 화면이 뜬다', async ({ page }) => {
    await createOwnPost(page, uniqueTitle('404용'));
    const deletedPostUrl = page.url();

    await page.locator('#btn-delete-post').click();
    await page.locator('#btn-confirm-delete').click();
    await page.waitForURL('/');

    await page.goto(deletedPostUrl);
    await expect(page.locator('.page-title')).toContainText('게시글을 찾을 수 없습니다');
    await expect(page.locator('.page-lead')).toContainText('삭제되었거나 존재하지 않습니다');

    const backLink = page.getByRole('link', { name: '게시판으로 돌아가기' });
    await expect(backLink).toBeVisible();
    await backLink.click();
    await expect(page).toHaveURL('/');
});
