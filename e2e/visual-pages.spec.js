import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

/**
 * style.css가 만드는 화면들의 기준선.
 *
 * visual.spec.js가 Bootstrap 쪽(버튼·폼·모달·토스트·alert)을 덮는다면, 여기서는 헤더·셸
 * 그리드·게시판 목록·페이지 이동·게시글 상세·댓글처럼 **style.css가 직접 그리는 부분**을 덮는다.
 * 943줄짜리 단일 CSS를 파셜로 나누기 전에 찍어 두고, 나눈 뒤와 비교한다.
 *
 * 실행 순서에 흔들리지 않도록 자기 데이터를 만들거나 검색으로 좁혀서 본다.
 */

const PIXEL_TOLERANCE = { maxDiffPixelRatio: 0.01 };

test.describe('공통 레이아웃', () => {
    test.use({ storageState: storageStateFor('user') });

    test('헤더 - 넓은 화면', async ({ page }) => {
        await page.setViewportSize({ width: 1280, height: 800 });
        await page.goto('/');
        await expect(page.locator('.kraft-topbar')).toHaveScreenshot('header-wide.png', PIXEL_TOLERANCE);
    });

    test('헤더 - 좁은 화면에서 메뉴를 펼친 상태', async ({ page }) => {
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto('/');
        await page.locator('#btn-nav-toggle').click();
        await expect(page.locator('#site-nav')).toBeVisible();
        await expect(page.locator('.kraft-topbar')).toHaveScreenshot('header-narrow-open.png', PIXEL_TOLERANCE);
    });

    test('게시판 머리말과 검색 영역', async ({ page }) => {
        await page.goto('/');
        await expect(page.locator('.board-head')).toHaveScreenshot('board-head.png', PIXEL_TOLERANCE);
    });
});

/**
 * 목록 한 줄의 생김새를 폭별로 고정한다. 지금까지 기준 이미지는 전부 1280px(넓은 화면)
 * 기준이라, 768px 안팎(표 형태로 바뀌면서 열이 가장 좁아지는 구간)과 390px(세로 카드로
 * 접히는 구간)의 회귀는 아무도 지켜주지 않았다 — 실제로 이번에 그 구간의 열 폭을 고쳤다.
 *
 * 시드 게시글("다른 사람의 글")로 검색해 한 줄만 본다. 다른 스펙이 만드는 글과 섞이면
 * 실행 순서에 따라 매번 다른 줄이 나온다.
 */
test.describe('목록 반응형', () => {
    test.use({ storageState: storageStateFor('user') });

    async function openSeedPostRow(page) {
        await page.goto(`/?q=${encodeURIComponent('다른 사람의 글')}`);
        const row = page.locator('.post-list__item').first();
        await expect(row).toBeVisible();
        return row;
    }

    // 여기는 기준 이미지를 쓰지 않는다(pager 위젯과 같은 이유 — 위 '빈 상태와 페이지 이동'
    // 참고). 이 카드는 한글 제목·닉네임을 포함하고 있어, 시스템에 설치된 한글 폰트가 실행
    // 환경마다 다른 지표(metric)로 대체될 수 있다 — 실측으로 이 스펙만 단독으로 돌리면
    // 항상 통과하는데 다른 스펙을 잔뜩 앞세운 뒤에 돌리면 높이가 정확히 1px 흔들렸다(폰트
    // 대체가 아닌 진짜 CSS 문제였다면 홀로 돌려도 재현됐어야 한다). 그래서 픽셀 대신 규칙을
    // 그대로 확인한다: 세로로 쌓이는가, 2줄까지만 보이는가.
    test('390px - 세로 카드로 접힌다', async ({ page }) => {
        await page.setViewportSize({ width: 390, height: 844 });
        const row = await openSeedPostRow(page);

        // <768px에서는 헤더 행이 숨고 각 항목이 세로로 쌓인다.
        await expect(page.locator('.post-list__head')).toBeHidden();
        const itemBox = await row.boundingBox();
        const titleBox = await row.locator('.post-list__title').boundingBox();
        const metaBox = await row.locator('.post-list__meta').boundingBox();
        // 제목이 카드 폭 전체를 쓰고(그리드로 좁아지지 않고), 메타 정보는 제목 아래에 있다.
        expect(titleBox.width).toBeGreaterThan(itemBox.width * 0.8);
        expect(metaBox.y).toBeGreaterThan(titleBox.y);

        // 제목은 2줄까지만 보인다 — line-clamp가 걸려 있어야 짧은 카드 높이가 유지된다.
        const lineClamp = await row.locator('.post-list__title').evaluate(
            (el) => getComputedStyle(el).webkitLineClamp,
        );
        expect(lineClamp).toBe('2');
    });

    test('768px - 표 형태로 바뀌는 가장 좁은 구간', async ({ page }) => {
        await page.setViewportSize({ width: 768, height: 900 });
        const row = await openSeedPostRow(page);
        await expect(row).toHaveScreenshot('post-list-row-768.png', {
            ...PIXEL_TOLERANCE,
            mask: [
                row.locator('.post-list__no'),
                row.locator('.post-list__date'),
                row.locator('.post-list__views'),
            ],
        });
    });
});

test.describe('게시글 상세', () => {
    test.use({ storageState: storageStateFor('user') });

    test('읽기 상태 - 제목·본문·추천·관리 버튼', async ({ page }) => {
        const title = uniqueTitle('시각');
        await page.goto('/posts/save');
        await page.locator('#title').fill(title);
        await page.locator('#content').fill('시각 회귀 기준선을 위한 본문입니다.');
        await page.locator('#btn-save').click();
        await page.waitForURL('/');
        await openPostByTitle(page, title);

        await expect(page.locator('#post-view')).toHaveScreenshot('post-view.png', {
            ...PIXEL_TOLERANCE,
            // 글 번호와 조회수는 실행마다 다르다. 제목도 uniqueTitle의 타임스탬프가 들어가
            // 실행마다 달라지므로 함께 가린다(개선 보고서 "시각 기준과 현재 화면의 불일치").
            mask: [page.locator('.post-byline'), page.locator('#post-title-text')],
        });
    });

    test('편집 상태 - 폼 전체', async ({ page }) => {
        const title = uniqueTitle('시각편집');
        await page.goto('/posts/save');
        await page.locator('#title').fill(title);
        await page.locator('#content').fill('편집 폼 기준선입니다.');
        await page.locator('#btn-save').click();
        await page.waitForURL('/');
        await openPostByTitle(page, title);
        await page.locator('#btn-edit').click();

        await expect(page.locator('#post-edit')).toHaveScreenshot('post-edit-form.png', {
            ...PIXEL_TOLERANCE,
            // 제목에 uniqueTitle의 타임스탬프가 들어간다.
            mask: [page.locator('#title')],
        });
    });

    test('댓글 목록', async ({ page }) => {
        const title = uniqueTitle('시각댓글');
        await page.goto('/posts/save');
        await page.locator('#title').fill(title);
        await page.locator('#content').fill('댓글 기준선입니다.');
        await page.locator('#btn-save').click();
        await page.waitForURL('/');
        await openPostByTitle(page, title);

        await page.locator('#comment-content').fill('기준선용 댓글입니다.');
        await page.locator('#btn-comment-save').click();
        await expect(page.locator('.comment-list__content')).toContainText('기준선용 댓글입니다.');

        await expect(page.locator('.comment-list')).toHaveScreenshot('comment-list.png', {
            ...PIXEL_TOLERANCE,
            // 작성 시각이 실행마다 다르다.
            mask: [page.locator('.comment-list__head small')],
        });
    });
});

test.describe('빈 상태와 페이지 이동', () => {
    test.use({ storageState: storageStateFor('user') });

    test('검색 결과 없음', async ({ page }) => {
        await page.goto('/?q=zzz-nothing-matches-this-zzz');
        await expect(page.locator('.empty-state')).toHaveScreenshot('empty-state.png', PIXEL_TOLERANCE);
    });

    test('페이지 이동 위젯', async ({ page }) => {
        await page.goto('/');
        const pager = page.locator('.pager');
        // 글이 한 페이지에 다 들어가면 위젯이 없을 수 있다. 있을 때만 확인한다.
        if (!(await pager.count())) {
            return;
        }

        // 여기는 기준 이미지를 쓰지 않는다. 위젯의 너비가 번호 링크 개수에 따라 달라지고,
        // 그 개수는 앞선 스펙이 글을 몇 개 만들었는지에 달려 있다(인메모리 DB를 모두 공유한다).
        // 가려도 자리 크기는 그대로라 비교가 실행 순서에 묶인다 — 실제로 신고 스펙이 글을
        // 더 만들자 깨졌다. 대신 첫 페이지에서 지켜야 할 규칙을 그대로 확인한다.
        await expect(pager.locator('.pager__step.is-disabled')).toHaveText('이전');
        await expect(pager.locator('a.pager__step')).toHaveText('다음');
        await expect(pager.locator('.pager__page.is-current')).toHaveText('1');
        await expect(pager.locator('.pager__status')).toContainText('/');
    });
});
