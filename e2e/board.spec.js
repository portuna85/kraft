import { test, expect, storageStateFor } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * 목록 화면의 빈 상태. 검색이 빗나간 것과 게시판이 비어 있는 것은 사용자가 할 일이 다르다
 * (조건을 지운다 / 첫 글을 쓴다). 예전에는 둘 다 "아직 게시글이 없습니다"였다.
 */
test('검색 결과가 없으면 게시판이 빈 것처럼 안내하지 않는다', async ({ page }) => {
    await page.goto('/?q=zzz-nothing-matches-this-zzz');

    await expect(page.locator('.empty-state')).toContainText('검색 조건에 맞는 게시글이 없습니다.');
    await expect(page.getByText('아직 게시글이 없습니다.')).toHaveCount(0);

    // 조건을 지우고 돌아갈 길을 준다 — 시드 데이터가 있으므로 목록이 다시 보인다.
    await page.locator('.empty-state').getByRole('link', { name: '전체 게시글 보기' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.locator('.post-list__item').first()).toBeVisible();
});
