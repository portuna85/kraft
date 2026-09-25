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

/**
 * F09: 목록의 열 제목은 aria-hidden(그리드 레이아웃용 시각적 헤더)이고 조회수·댓글수는
 * 숫자만 렌더링돼, 그 숫자가 무엇을 뜻하는지 스크린리더가 읽어줄 텍스트가 없었다.
 */
test('조회수·댓글수 옆에 스크린리더용 이름표가 붙는다', async ({ page }) => {
    await page.goto('/');

    const item = page.locator('.post-list__item').first();
    await expect(item.locator('.post-list__views')).toContainText(/조회\s*\d+/);
    await expect(item.locator('.post-list__comments')).toContainText(/댓글\s*\d+/);
});

/**
 * 992~1199px 구간은 픽셀 기준 이미지 대신 규칙으로 고정한다. 이 폭에서 오른쪽 안내
 * (.kraft-aside)가 본문 아래로 떨어지면 폭이 남는데도 한 줄만 쓰는 셈이 된다 — 2열 전환을
 * 1200px에서 992px로 낮춘 것이 바로 이 구간을 겨냥한 것이다.
 *
 * 픽셀 비교로 고정하지 않는 이유는 pager 위젯과 같다(위 테스트 참고) — 구체적인 값보다
 * "안내가 본문과 같은 줄에서 시작하는가"라는 규칙 자체가 중요하다.
 */
test('992~1199px에서는 안내가 본문 옆에 남고 아래로 떨어지지 않는다', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 900 });
    await page.goto('/');

    const main = page.locator('.kraft-main');
    const aside = page.locator('.kraft-aside');
    await expect(aside).toBeVisible();

    const [mainBox, asideBox] = await Promise.all([main.boundingBox(), aside.boundingBox()]);
    // 같은 줄에서 시작해야 나란히 놓인 것이다. 아래로 떨어졌다면 y가 본문의 아래쪽으로 밀린다.
    expect(Math.abs(mainBox.y - asideBox.y)).toBeLessThan(2);
    // 안내가 본문의 오른쪽에 있어야 한다(왼쪽 열이 아니라 두 번째 열).
    expect(asideBox.x).toBeGreaterThan(mainBox.x);
});

/**
 * 검색·분류 조건이 걸려 있다는 사실이 검색 폼만 봐서는 드러나지 않았다(분류 select의
 * is-active 테두리만으로는 눈에 잘 띄지 않는다). 조건이 있을 때만 요약을 보여주고,
 * 초기화하면 조건 없는 목록으로 돌아간다.
 */
test('검색 조건이 있으면 적용된 조건 요약이 보이고, 초기화하면 사라진다', async ({ page }) => {
    await page.goto('/?q=zzz-nothing-matches-this-zzz');

    const summary = page.locator('.board-filter-summary');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText('zzz-nothing-matches-this-zzz');

    await summary.getByRole('link', { name: '초기화' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.locator('.board-filter-summary')).toHaveCount(0);
});

test('검색·분류 조건이 없으면 적용된 조건 요약이 보이지 않는다', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('.board-filter-summary')).toHaveCount(0);
});
