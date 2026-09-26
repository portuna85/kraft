import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * "더 보기"(10단계) 테스트가 쓸 만큼(기본 페이지 크기 10보다 많이) 검색으로 좁혀지는 글을
 * API로 만든다. CSRF는 static 리소스 체인(/css, /js, /images)만 꺼져 있고 나머지(로그인
 * 폼·이 API 포함)는 그대로 걸려 있다 — page.request는 폼을 거치지 않으므로 메타 태그의
 * 토큰을 직접 읽어 헤더에 실어야 한다(core/http.js의 csrfHeaders()와 같은 방식).
 */
async function createPosts(page, count, titlePrefix) {
    await page.goto('/');
    const token = await page.locator('meta[name="_csrf"]').getAttribute('content');
    const headerName = await page.locator('meta[name="_csrf_header"]').getAttribute('content');

    for (let i = 0; i < count; i += 1) {
        const response = await page.request.post('/api/v1/posts', {
            headers: { [headerName]: token },
            data: {
                title: `${titlePrefix}-${i}`,
                content: `더 보기 테스트용 본문 ${i}`,
                category: 'FREE',
            },
        });
        expect(response.ok(), `글 생성 요청이 성공해야 한다(${response.status()})`).toBe(true);
    }
}

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

/**
 * 9단계: 서버는 이미 id/viewCount/updatedAt 정렬을 지원한다(PostSortPolicy). 화면에서
 * 조회순을 고르면 URL에 sort가 실리고, 조건 요약에 칩으로 보이고, 초기화하면 기본
 * 정렬(최신 등록순)로 돌아간다.
 */
test('정렬을 조회순으로 바꾸면 URL에 반영되고 조건 요약에 칩으로 보인다', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#search-sort')).toHaveValue('');

    await page.locator('#search-sort').selectOption('viewCount,desc');
    await page.getByRole('button', { name: '검색' }).click();

    await expect(page).toHaveURL(/[?&]sort=viewCount%2Cdesc/);
    await expect(page.locator('#search-sort')).toHaveClass(/is-active/);
    const summary = page.locator('.board-filter-summary');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText('조회순');

    await summary.getByRole('link', { name: '초기화' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.locator('#search-sort')).toHaveValue('');
    await expect(page.locator('.board-filter-summary')).toHaveCount(0);
});

test('정렬을 고른 채 페이지를 이동해도 정렬이 유지된다', async ({ page }) => {
    // size=1로 강제로 여러 페이지를 만든다 — 기본 크기(10)면 시드 글 수에 따라 페이지가
    // 하나뿐일 수 있어 pager 자체가 렌더링되지 않는다.
    await page.goto('/?sort=updatedAt,desc&page=0&size=1');

    const nextLink = page.getByRole('link', { name: '다음' });
    await expect(nextLink).toHaveAttribute('href', /sort=updatedAt,desc/);

    await nextLink.click();
    await expect(page).toHaveURL(/[?&]sort=updatedAt,desc/);
    await expect(page.locator('#search-sort')).toHaveValue('updatedAt,desc');
});

/**
 * 10단계: "더 보기"는 JSON API(/api/v1/posts, 새 API 아님)로 다음 페이지를 이어 붙인다.
 * 페이지 이동(pager)과 공존하며, JS가 로드돼야 버튼이 보인다(index.html의 hidden 초기값).
 */
test.describe('더 보기', () => {
    test('버튼을 누르면 다음 페이지를 이어 붙이고, 마지막이면 버튼이 사라진다', async ({ page }) => {
        const prefix = uniqueTitle('더보기');
        await createPosts(page, 11, prefix);

        await page.goto(`/?q=${encodeURIComponent(prefix)}`);
        await expect(page.locator('.post-list__item')).toHaveCount(10);

        const button = page.locator('#btn-load-more');
        await expect(button).toBeVisible();
        await expect(button).toHaveText('더 보기');

        await button.click();
        await expect(page.locator('.post-list__item')).toHaveCount(11);
        await expect(button).toBeHidden();
        await expect(page.locator('#load-more-status')).toHaveText('마지막 글까지 모두 불러왔습니다.');

        // 이어 붙인 새 글도 같은 모양(분류 배지 포함)으로 렌더링됐는지 확인한다.
        const appended = page.locator('.post-list__item', { hasText: `${prefix}-10` });
        await expect(appended.locator('.post-list__category')).toHaveText('자유');
    });

    test('버튼을 누르면 새로 붙은 첫 글의 제목으로 포커스가 이동한다', async ({ page }) => {
        const prefix = uniqueTitle('포커스');
        await createPosts(page, 11, prefix);

        await page.goto(`/?q=${encodeURIComponent(prefix)}`);
        await page.locator('#btn-load-more').click();

        const focused = page.locator(':focus');
        await expect(focused).toHaveClass(/post-list__title/);
    });

    test('정렬을 고른 채 더 보기를 누르면 요청에 정렬이 실린다', async ({ page }) => {
        const prefix = uniqueTitle('정렬더보기');
        await createPosts(page, 11, prefix);

        await page.goto(`/?q=${encodeURIComponent(prefix)}&sort=viewCount,desc`);

        const requestPromise = page.waitForRequest((req) =>
            req.url().includes('/api/v1/posts') && req.url().includes('sort=viewCount'));
        await page.locator('#btn-load-more').click();
        const request = await requestPromise;
        expect(request.url()).toContain(`q=${encodeURIComponent(prefix)}`);
    });

    test('불러오기에 실패하면 재시도 안내가 보이고 버튼을 다시 누를 수 있다', async ({ page }) => {
        const prefix = uniqueTitle('실패');
        await createPosts(page, 11, prefix);

        await page.goto(`/?q=${encodeURIComponent(prefix)}`);

        let requestCount = 0;
        // Playwright의 문자열 glob 패턴에서 "?"는 "임의의 문자 한 개"를 뜻하는 특수문자라
        // 실제 물음표(쿼리스트링 구분자)에 쓸 수 없다 — 정규식으로 지정한다.
        await page.route(/\/api\/v1\/posts\?/, (route) => {
            requestCount += 1;
            if (requestCount === 1) {
                return route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
            }
            return route.continue();
        });

        const button = page.locator('#btn-load-more');
        await button.click();

        const status = page.locator('#load-more-status');
        await expect(status).toHaveClass(/is-error/);
        await expect(status).toContainText('다시');
        await expect(button).toBeEnabled();

        // 버튼을 다시 누르면(같은 page 파라미터로 재시도) 성공한다.
        await button.click();
        await expect(page.locator('.post-list__item')).toHaveCount(11);
    });
});
