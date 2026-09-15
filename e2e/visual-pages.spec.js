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
            // 글 번호와 조회수는 실행마다 다르다.
            mask: [page.locator('.post-byline')],
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
