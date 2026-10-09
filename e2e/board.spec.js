import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * "더 보기"(10단계) 테스트가 쓸 만큼(기본 페이지 크기 10보다 많이) 검색으로 좁혀지는 글을
 * API로 만든다. CSRF는 static 리소스 체인(/css, /js, /images)만 꺼져 있고 나머지(로그인
 * 폼·이 API 포함)는 그대로 걸려 있다 — page.request는 폼을 거치지 않으므로 메타 태그의
 * 토큰을 직접 읽어 헤더에 실어야 한다(core/http.js의 csrfHeaders()와 같은 방식).
 */
async function createPosts(page, count, titlePrefix) {
    await page.goto('/community');
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
    await page.goto('/community?q=zzz-nothing-matches-this-zzz');

    await expect(page.locator('.empty-state')).toContainText('검색 조건에 맞는 게시글이 없습니다.');
    await expect(page.getByText('아직 게시글이 없습니다.')).toHaveCount(0);

    // 조건을 지우고 돌아갈 길을 준다 — 시드 데이터가 있으므로 목록이 다시 보인다.
    await page.locator('.empty-state').getByRole('link', { name: '전체 게시글 보기' }).click();
    await expect(page).toHaveURL('/community');
    await expect(page.locator('.post-list__item').first()).toBeVisible();
});

/**
 * 목록의 열 제목은 aria-hidden(그리드 레이아웃용 시각적 헤더)이고 조회수·댓글수는
 * 숫자만 렌더링돼, 그 숫자가 무엇을 뜻하는지 스크린리더가 읽어줄 텍스트가 없었다.
 */
test('조회수·댓글수 옆에 스크린리더용 이름표가 붙는다', async ({ page }) => {
    await page.goto('/community');

    const item = page.locator('.post-list__item').first();
    await expect(item.locator('.post-list__views')).toContainText(/조회\s*\d+/);
    await expect(item.locator('.post-list__comments')).toContainText(/댓글\s*\d+/);
});

/**
 * 기본 정렬이 등록순(id)이므로 날짜 열도 등록일을 보인다 — 수정 시각을 보이면
 * 오래된 글을 고쳤을 때 "최신 등록순" 중간에 오늘 날짜가 찍혀 순서가 뒤섞여 보인다.
 * 실제로 수정된 글만 "(수정됨)"이 붙는다.
 */
test('등록만 하고 수정하지 않은 글에는 "(수정됨)"이 붙지 않는다', async ({ page }) => {
    const title = uniqueTitle('날짜표시');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('수정하지 않을 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL(/\/posts\/update\/\d+$/);

    await page.goto(`/community?q=${encodeURIComponent(title)}`);
    const row = page.locator('.post-list__item').filter({ hasText: title });
    await expect(row.locator('.post-list__date')).not.toContainText('수정됨');
});

test('글을 수정하면 목록 날짜 옆에 "(수정됨)"이 붙는다', async ({ page }) => {
    const title = uniqueTitle('날짜표시');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('곧 수정할 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL(/\/posts\/update\/\d+$/);

    await page.locator('#btn-edit').click();
    await page.locator('#content').fill('수정된 본문입니다.');
    await page.locator('#btn-update').click();
    await page.waitForURL(/\/posts\/update\/\d+$/);
    // 새로고침 이동이 끝났다는 것을 화면 렌더로 확인한 뒤에 다음 페이지로 이동한다 —
    // 그러지 않으면 아직 끝나지 않은 이전 이동과 겹쳐 다음 goto가 중단될 수 있다.
    await expect(page.locator('#flash')).toContainText('글이 수정되었습니다.');

    await page.goto(`/community?q=${encodeURIComponent(title)}`);
    const row = page.locator('.post-list__item').filter({ hasText: title });
    await expect(row.locator('.post-list__date')).toContainText('(수정됨)');
});

/**
 * 시드 데이터의 "공지 게시글"(NOTICE)이 검색·분류로 좁히지 않은 첫 페이지에서만
 * 별도 고정 영역(.post-list--pinned)에 보인다. 본목록에서도 여전히 최신순 자리 그대로
 * 보인다 — 고정은 "복제해서 보여주는 것"이지 본목록에서 빼는 것이 아니다.
 */
test.describe('공지 고정', () => {
    test('검색·분류 없는 첫 페이지에는 고정 영역에 공지가 보인다', async ({ page }) => {
        await page.goto('/community');
        await expect(page.locator('.post-list--pinned .post-list__item')).toContainText('공지 게시글');
    });

    test('검색 결과 화면에는 고정 영역이 없다', async ({ page }) => {
        await page.goto('/community?q=zzz-nothing-matches-this-zzz');
        await expect(page.locator('.post-list--pinned')).toHaveCount(0);
    });

    test('분류를 고르면 고정 영역이 없다', async ({ page }) => {
        await page.goto('/community?category=FREE');
        await expect(page.locator('.post-list--pinned')).toHaveCount(0);
    });

    test('2페이지에는 고정 영역이 없다', async ({ page }) => {
        // 2페이지가 실제로 존재해야 한다 — 이 스펙만 따로 돌리면 시드 글 몇 개뿐이라 2페이지가
        // 없어 page=1 요청이 마지막 유효 페이지(0쪽)로 리다이렉트되어 버린다(PostPageController).
        await createPosts(page, 15, uniqueTitle('공지고정-2페이지'));

        await page.goto('/community?page=1');
        await expect(page).toHaveURL(/page=1/);
        await expect(page.locator('.post-list--pinned')).toHaveCount(0);
    });
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
    await page.goto('/community');

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
    await page.goto('/community?q=zzz-nothing-matches-this-zzz');

    const summary = page.locator('.board-filter-summary');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText('zzz-nothing-matches-this-zzz');

    await summary.getByRole('link', { name: '초기화' }).click();
    await expect(page).toHaveURL('/community');
    await expect(page.locator('.board-filter-summary')).toHaveCount(0);
});

test('검색·분류 조건이 없으면 적용된 조건 요약이 보이지 않는다', async ({ page }) => {
    await page.goto('/community');

    await expect(page.locator('.board-filter-summary')).toHaveCount(0);
});

/**
 * 9단계: 서버는 이미 id/viewCount/updatedAt 정렬을 지원한다(PostSortPolicy). 화면에서
 * 조회순을 고르면 URL에 sort가 실리고, 조건 요약에 칩으로 보이고, 초기화하면 기본
 * 정렬(최신 등록순)로 돌아간다.
 */
test('정렬을 조회순으로 바꾸면 URL에 반영되고 조건 요약에 칩으로 보인다', async ({ page }) => {
    await page.goto('/community');
    await expect(page.locator('#search-sort')).toHaveValue('');

    await page.locator('#search-sort').selectOption('viewCount,desc');
    await page.getByRole('button', { name: '검색' }).click();

    await expect(page).toHaveURL(/[?&]sort=viewCount%2Cdesc/);
    await expect(page.locator('#search-sort')).toHaveClass(/is-active/);
    const summary = page.locator('.board-filter-summary');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText('조회순');

    await summary.getByRole('link', { name: '초기화' }).click();
    await expect(page).toHaveURL('/community');
    await expect(page.locator('#search-sort')).toHaveValue('');
    await expect(page.locator('.board-filter-summary')).toHaveCount(0);
});

test('정렬을 고른 채 페이지를 이동해도 정렬이 유지된다', async ({ page }) => {
    // size=1로 강제로 여러 페이지를 만든다 — 기본 크기(10)면 시드 글 수에 따라 페이지가
    // 하나뿐일 수 있어 pager 자체가 렌더링되지 않는다.
    await page.goto('/community?sort=updatedAt,desc&page=0&size=1');

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
/**
 * 검색어가 있으면 전체 건수를 세지 않는다(LIKE '%kw%'는 인덱스를 못 타서 COUNT가 항상 전체
 * 스캔이다). 화면은 총 개수·번호 목록 없이 이전·다음과 "N페이지"만 보여 준다.
 */
test.describe('검색 결과(전체 건수를 세지 않음)', () => {
    test('총 개수와 번호 목록 없이 이전·다음으로 이동한다', async ({ page }) => {
        const prefix = uniqueTitle('검색이동');
        await createPosts(page, 11, prefix); // 페이지 크기 10 → 10 + 1

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);

        await expect(page.locator('.post-list__item')).toHaveCount(10);
        await expect(page.locator('.board-head__count')).toHaveCount(0);
        const pager = page.locator('nav.pager');
        await expect(pager.locator('.pager__numbers')).toHaveCount(0);
        await expect(pager.locator('.pager__status')).toHaveText('1페이지');
        await expect(pager.locator('[data-role="next"]')).toHaveText('다음');

        await pager.getByRole('link', { name: '다음' }).click();

        await expect(page).toHaveURL(/page=1/);
        await expect(page.locator('.post-list__item')).toHaveCount(1);
        await expect(pager.locator('.pager__status')).toHaveText('2페이지');
        // 마지막 페이지라 "다음"은 비활성이고 "이전"은 링크다.
        await expect(pager.locator('[data-role="next"]')).toHaveClass(/is-disabled/);
        await pager.getByRole('link', { name: '이전' }).click();
        await expect(page.locator('.post-list__item')).toHaveCount(10);
    });

    test('결과가 한 페이지에 다 들어오면 pager를 그리지 않고 총 개수도 없다', async ({ page }) => {
        const prefix = uniqueTitle('검색한쪽');
        await createPosts(page, 3, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);

        await expect(page.locator('.post-list__item')).toHaveCount(3);
        await expect(page.locator('nav.pager')).toHaveCount(0);
        await expect(page.locator('.board-head__count')).toHaveCount(0);
    });

    test('결과가 없는 범위 밖 페이지는 검색 조건을 유지한 채 첫 페이지로 돌아간다', async ({ page }) => {
        const prefix = uniqueTitle('검색범위밖');
        await createPosts(page, 2, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}&page=9`);

        await expect(page).not.toHaveURL(/page=/);
        await expect(page).toHaveURL(new RegExp(`q=${encodeURIComponent(prefix)}`));
        await expect(page.locator('.post-list__item')).toHaveCount(2);
    });
});

test.describe('더 보기', () => {
    test('버튼을 누르면 다음 페이지를 이어 붙이고, 마지막이면 버튼이 사라진다', async ({ page }) => {
        const prefix = uniqueTitle('더보기');
        await createPosts(page, 11, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);
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

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);
        await page.locator('#btn-load-more').click();

        const focused = page.locator(':focus');
        await expect(focused).toHaveClass(/post-list__title/);
    });

    test('정렬을 고른 채 더 보기를 누르면 요청에 정렬이 실린다', async ({ page }) => {
        const prefix = uniqueTitle('정렬더보기');
        await createPosts(page, 11, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}&sort=viewCount,desc`);

        const requestPromise = page.waitForRequest((req) =>
            req.url().includes('/api/v1/posts') && req.url().includes('sort=viewCount'));
        await page.locator('#btn-load-more').click();
        const request = await requestPromise;
        expect(request.url()).toContain(`q=${encodeURIComponent(prefix)}`);
    });

    test('불러오기에 실패하면 재시도 안내가 보이고 버튼을 다시 누를 수 있다', async ({ page }) => {
        const prefix = uniqueTitle('실패');
        await createPosts(page, 11, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);

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

    /**
     * 13단계: 더 보기로 실제 화면에 붙은 범위를 pager(번호·"다음"·상태 문구)에 반영한다.
     * 예전에는 pager가 서버가 처음 그린 1페이지에 멈춰 있어, 더 보기를 여러 번 눌러도
     * "다음"을 누르면 이미 화면에 있는 페이지로 되돌아갔다.
     */
    test('검색 결과에서 더 보기를 두 번 눌러 마지막 페이지까지 불러오면 pager가 "N–M페이지"로 그 범위를 알린다', async ({ page }) => {
        const prefix = uniqueTitle('페이저갱신');
        await createPosts(page, 25, prefix); // 페이지 크기 10 → 총 3페이지(10·10·5)

        // 검색 결과는 전체 건수를 세지 않는다 — 번호 목록과 "/ 전체" 없이 이전·다음과 "N페이지"만 있다.
        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);
        const pager = page.locator('nav.pager');
        await expect(pager.locator('.pager__status')).toHaveText('1페이지');
        await expect(pager.locator('.pager__page')).toHaveCount(0);

        const button = page.locator('#btn-load-more');
        await button.click(); // 2페이지(index 1)까지 붙음
        await expect(pager.locator('.pager__status')).toHaveText('1–2페이지');
        await button.click(); // 3페이지(index 2, 마지막)까지 붙음

        await expect(page.locator('.post-list__item')).toHaveCount(25);
        await expect(button).toBeHidden();
        await expect(pager.locator('.pager__status')).toHaveText('1–3페이지');

        // "다음"은 더 불러올 페이지가 없으므로 "이전 없음"과 같은 비활성 모양이 된다.
        const nextEl = pager.locator('[data-role="next"]');
        await expect(nextEl).toHaveClass(/is-disabled/);
        expect(await nextEl.evaluate((el) => el.tagName)).toBe('SPAN');
    });

    /**
     * 검색어 없는 목록은 총 건수와 번호 pager를 그대로 유지한다(BE-08은 검색만 COUNT를 뺀다).
     * 글 수는 다른 스펙이 공유 DB에 만든 글에 따라 달라지므로 구체적인 숫자 대신 형태만 본다.
     */
    test('검색 아닌 목록은 더 보기 후에도 번호 pager와 "N–M / 전체"를 유지한다', async ({ page }) => {
        await createPosts(page, 11, uniqueTitle('번호페이저'));

        await page.goto('/community?category=FREE');
        await expect(page.locator('.board-head__count')).toContainText('총');
        const pager = page.locator('nav.pager');
        await expect(pager.locator('.pager__numbers')).toBeVisible();
        await expect(pager.locator('.pager__status')).toHaveText(/^\s*1 \/ \d+\s*$/);
        await expect(pager.locator('.pager__page[data-page="0"]')).toHaveAttribute('aria-current', 'page');

        await page.locator('#btn-load-more').click();

        await expect(pager.locator('.pager__status')).toHaveText(/^1–2 \/ \d+$/);
        await expect(pager.locator('.pager__page[data-page="1"]')).toHaveClass(/is-current/);
    });

    /**
     * 정렬 기준(조회순 등)에 따라 목록 순서가 바뀔 수 있어, 이미 화면에 있는 글이 다음
     * 페이지 응답에 다시 섞여 들어올 수 있다 — 중복 없이 걸러야 한다.
     */
    test('이미 붙은 글이 다음 페이지 응답에 다시 섞여 와도 중복으로 붙지 않는다', async ({ page }) => {
        const prefix = uniqueTitle('중복방지');
        await createPosts(page, 11, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);
        const firstRowId = await page.locator('.post-list__item').first().getAttribute('data-post-id');

        await page.route(/\/api\/v1\/posts\?/, async (route) => {
            const response = await route.fetch();
            const body = await response.json();
            // 이미 1페이지에 있던 글을 2페이지 응답 맨 앞에 다시 끼워 넣는다.
            const duplicate = { ...body.content[0], id: Number(firstRowId) };
            await route.fulfill({
                response,
                json: { ...body, content: [duplicate, ...body.content] },
            });
        });

        await page.locator('#btn-load-more').click();

        // 서버가 준 새 글 1개만 붙고, 끼워 넣은 중복은 걸러진다.
        await expect(page.locator('.post-list__item')).toHaveCount(11);
        await expect(page.locator(`.post-list__item[data-post-id="${firstRowId}"]`)).toHaveCount(1);
    });

    /**
     * bfcache가 없는 새로고침(page.reload는 항상 새 탐색이라 bfcache를 타지 않는다 —
     * bfcache가 있었다면 이 모듈이 다시 실행되지 않고 DOM이 그대로 남아 애초에 문제가 없다)
     * 뒤에도 sessionStorage에 남긴 상태로 이어 붙인 행이 네트워크 없이 되살아난다.
     */
    test('더 보기로 불러온 뒤 새로고침해도 불러온 글이 그대로 남는다', async ({ page }) => {
        const prefix = uniqueTitle('새로고침복원');
        await createPosts(page, 11, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);
        await page.locator('#btn-load-more').click();
        await expect(page.locator('.post-list__item')).toHaveCount(11);
        await expect(page.locator('#btn-load-more')).toBeHidden();

        await page.reload();

        await expect(page.locator('.post-list__item')).toHaveCount(11);
        await expect(page.locator('#btn-load-more')).toBeHidden();
        await expect(page.locator('#load-more-status')).toHaveText('마지막 글까지 모두 불러왔습니다.');
    });

    test('조건이 다른 목록으로 이동하면 이전에 불러온 상태를 복원하지 않는다', async ({ page }) => {
        const prefix = uniqueTitle('조건다름복원');
        await createPosts(page, 11, prefix);

        await page.goto(`/community?q=${encodeURIComponent(prefix)}`);
        await page.locator('#btn-load-more').click();
        await expect(page.locator('.post-list__item')).toHaveCount(11);

        // 같은 프리픽스로 검색하지만 분류를 좁히면 다른 저장 키를 쓴다 — 복원되지 않는다.
        await page.goto(`/community?q=${encodeURIComponent(prefix)}&category=FREE`);
        await expect(page.locator('.post-list__item')).toHaveCount(10);
        await expect(page.locator('#btn-load-more')).toBeVisible();
    });
});
