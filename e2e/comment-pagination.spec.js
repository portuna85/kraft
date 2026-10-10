import { test, expect, storageStateFor, uniqueTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

async function openOwnPost(page) {
    const title = uniqueTitle('댓글');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('댓글 테스트용 글입니다.');
    await page.locator('#btn-save').click();
    // 등록 후 목록이 아니라 방금 쓴 글로 바로 이동한다.
    await page.waitForURL(/\/posts\/update\/\d+$/);
}

/**
 * 페이지네이션의 준비 데이터는 실제 API로 순차 생성한다. 등록·답글 UI는 별도 테스트와 각 회귀 시나리오의 새 댓글/답글로 검증하며, 21회씩 동일 폼을 반복 조작하지 않는다.
 */
async function seedComments(page, count, parentId = null) {
    const postId = new URL(page.url()).pathname.split('/').pop();
    const token = await page.locator('meta[name="_csrf"]').getAttribute('content');
    const headerName = await page.locator('meta[name="_csrf_header"]').getAttribute('content');
    for (let i = 1; i <= count; i += 1) {
        const response = await page.request.post(`/api/v1/posts/${postId}/comments`, {
            headers: { [headerName]: token },
            data: { content: `시드 ${parentId === null ? '댓글' : '답글'} ${i}`, parentId },
        });
        expect(response.ok(), `시드 생성 요청이 성공해야 한다(${response.status()})`).toBe(true);
    }
}

/** 새 댓글을 더한 뒤 "더 보기"로 다음 페이지를 받으면 방금 더한 댓글이 서버 페이지에도 다시 포함될 수 있다. id 기준 병합이 없으면 같은 댓글이 두 번 보인다. */
test('새 댓글 등록 후 더 보기를 눌러도 중복 없이 합쳐진다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await seedComments(page, 21);

    await page.reload();
    await expect(page.locator('.comment-list__content')).toHaveCount(20);
    await expect(page.locator('#btn-comments-load-more')).toBeVisible();

    await page.locator('#comment-content').fill('페이지 사이에 새로 단 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toHaveCount(21);
    await expect(page.locator('.comment-list__content').last()).toContainText('페이지 사이에 새로 단 댓글');

    await page.locator('#btn-comments-load-more').click();

    // 서버의 마지막 페이지에는 방금 단 댓글(22번째)도 다시 포함되지만, id 기준 병합이 중복을 걸러내야 한다.
    await expect(page.locator('.comment-list__content')).toHaveCount(22);
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');
});

/**
 * http.js의 타임아웃은 fetch가 헤더를 받은 뒤 본문을 다 읽을 때까지도 적용되어야 한다. Playwright의 route.fulfill/continue는 부분 스트리밍 응답을 만들 수 없으므로(본문은 항상 한 번에 완성된 값이어야 한다),
 * 헤더만 즉시 보내고 본문을 끝내지 않는 e2e 전용 엔드포인트(/e2e/slow-body)로 같은 출처 안에서 리다이렉트해 재현한다.
 */
test('더 보기 응답의 헤더만 오고 본문이 멈추면 타임아웃으로 처리된다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await seedComments(page, 21);
    await page.reload();
    await expect(page.locator('#btn-comments-load-more')).toBeVisible();

    await page.route(/\/api\/v1\/posts\/\d+\/comments\/page/, (route) =>
        route.continue({ url: new URL('/e2e/slow-body', route.request().url()).toString() }),
    );

    await page.locator('#btn-comments-load-more').click();

    await expect(page.locator('#app-toast-body')).toContainText('요청 시간이 초과되었습니다', {
        timeout: 20_000,
    });
    await expect(page.locator('#btn-comments-load-more')).toBeEnabled();
});

/** 더 보기 실패는 토스트로도 알리지만 토스트는 지나가면 사라진다. 버튼 자리에 계속 보이는 안내와 재시도 버튼을 함께 제공한다. */
test('더 보기가 실패하면 인라인 재시도 안내가 남고, 다시 시도하면 이어서 불러온다', async ({ page }) => {
    await openOwnPost(page);
    await seedComments(page, 21);
    await page.reload();
    await expect(page.locator('#btn-comments-load-more')).toBeVisible();

    let shouldFail = true;
    await page.route(/\/api\/v1\/posts\/\d+\/comments\/page/, async (route) => {
        if (shouldFail) {
            shouldFail = false;
            await route.fulfill({ status: 500, contentType: 'application/json', body: '{"detail":"일시적인 오류"}' });
            return;
        }
        await route.continue();
    });

    await page.locator('#btn-comments-load-more').click();

    const loadError = page.locator('.comments__load-error');
    await expect(loadError).toBeVisible();
    await expect(loadError).toContainText('댓글을 더 불러오지 못했습니다.');

    await loadError.getByRole('button', { name: '다시 시도' }).click();

    await expect(loadError).toHaveCount(0);
    await expect(page.locator('.comment-list__item')).toHaveCount(21);
});

/**
 * 회귀: 서버는 최초 페이지에서 최상위 댓글 하나당 답글을 20개까지만 내려준다. 전역 상한 방식이면 답글이 많은 부모가 상한을 혼자 다 써 나머지가 영원히 숨겨지므로, 부모별 상한과 "답글 더 보기"로 항상 나머지에 도달할 수 있어야 한다.
 */
test('회귀: 답글이 21개면 새로고침 후 20개만 보이고, 답글 더 보기로 나머지에 도달한다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await page.locator('#comment-content').fill('답글이 많이 달릴 부모 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    await seedComments(page, 21, Number(await topLevelItem.getAttribute('data-comment-id')));

    // 실제 서버의 부모별 상한을 적용한 초기 화면을 받는다.
    await page.reload();
    const reloadedTopLevelItem = page.locator('.comment-list > .comment-list__item').first();
    const replies = reloadedTopLevelItem.locator('.comment-list__replies .comment-list__item');
    await expect(replies).toHaveCount(20);
    const loadMoreReplies = reloadedTopLevelItem.locator('.btn-comment-replies-load-more');
    await expect(loadMoreReplies).toBeVisible();

    await loadMoreReplies.click();

    await expect(replies).toHaveCount(21);
    await expect(loadMoreReplies).toHaveCount(0);
});

/**
 * 회귀: 답글 20개만 받은 상태에서 새 답글을 쓰면 "답글 더 보기"가 화면 배열의 마지막 id(새 답글)를 커서로 보내 아직 받지 않은 21번째 답글을 건너뛴다.
 * 또 답글 삭제가 부모의 답글 수를 줄이지 않으면, 이어서 부모를 지울 때 개수가 음수가 된다.
 */
test('회귀: 새 답글을 쓴 뒤 답글 더 보기로 빠짐없이 받고, 답글·부모 삭제 후 개수가 0이다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await page.locator('#comment-content').fill('커서 회귀용 부모 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    await seedComments(page, 21, Number(await topLevelItem.getAttribute('data-comment-id')));

    await page.reload();
    const parent = page.locator('.comment-list > .comment-list__item').first();
    const replies = parent.locator('.comment-list__replies .comment-list__item');
    await expect(replies).toHaveCount(20);

    await parent.locator(':scope > .comment-view .btn-comment-reply').click();
    await parent.locator('.comment-reply-form textarea').fill('새로 쓴 답글');
    await parent.locator('.btn-comment-reply-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    await expect(replies).toHaveCount(21);

    await parent.locator('.btn-comment-replies-load-more').click();
    await expect(replies).toHaveCount(22);
    await expect(parent.locator('.comment-list__replies .comment-list__content', { hasText: /^\s*시드 답글 21\s*$/ })).toHaveCount(1);
    await expect(parent.locator('.comment-list__replies .comment-list__content', { hasText: '새로 쓴 답글' })).toHaveCount(1);
    await expect(page.locator('#comments-heading')).toContainText('댓글 23개');

    await replies.first().locator('.btn-comment-delete').click();
    await page.locator('#btn-confirm-delete').click();
    await expect(page.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');

    // 부모에는 아직 답글 21개가 남아 있어 소프트 삭제된다 — 행이 남으므로 개수는 줄지 않는다(하드 삭제였다면 22개가 통째로 빠져 0개가 됐을 것이다).
    await parent.locator(':scope > .comment-view .btn-comment-delete').click();
    await page.locator('#btn-confirm-delete').click();
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');
});

/**
 * "더 보기" 응답이 지연되는 동안 새 댓글을 등록해 totalCount를 로컬에서 22로 올려도, 늦게 도착한 페이지 응답의 totalCount(21)로 되돌아가면 안 된다. mutationSeq가 이 되돌림을 막는다("응답 순서 뒤집기" 재현).
 */
test('더 보기 응답이 지연되는 동안 등록한 댓글의 개수가 되돌아가지 않는다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await seedComments(page, 21);
    await page.reload();
    await expect(page.locator('.comment-list__content')).toHaveCount(20);
    await expect(page.locator('#btn-comments-load-more')).toBeVisible();

    let releasePage;
    const gate = new Promise((resolve) => {
        releasePage = resolve;
    });
    await page.route(/\/api\/v1\/posts\/\d+\/comments\/page/, async (route) => {
        await gate;
        await route.continue();
    });

    await page.locator('#btn-comments-load-more').click();

    await page.locator('#comment-content').fill('더 보기 진행 중에 단 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toHaveCount(21);
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');

    releasePage();
    await expect(page.locator('#btn-comments-load-more')).toBeHidden();
    // 더 보기 응답의 totalCount(21)가 방금 로컬에서 올린 22를 덮어쓰면 안 된다.
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');
    await expect(page.locator('.comment-list__content')).toHaveCount(22);
});

/** 같은 경쟁을 답글 등록으로 재현한다 — 답글도 totalCount를 증감하므로 같은 mutationSeq 가드를 거친다. */
test('더 보기 응답이 지연되는 동안 등록한 답글의 개수가 되돌아가지 않는다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await seedComments(page, 21);
    await page.reload();
    await expect(page.locator('.comment-list__content')).toHaveCount(20);

    let releasePage;
    const gate = new Promise((resolve) => {
        releasePage = resolve;
    });
    await page.route(/\/api\/v1\/posts\/\d+\/comments\/page/, async (route) => {
        await gate;
        await route.continue();
    });

    await page.locator('#btn-comments-load-more').click();

    await page.locator('.btn-comment-reply').first().click();
    const replyForm = page.locator('.comment-reply-form').first();
    await expect(replyForm).toBeVisible();
    await replyForm.locator('textarea').fill('더 보기 진행 중에 단 답글');
    await replyForm.getByRole('button', { name: '답글 등록' }).click();
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');

    releasePage();
    await expect(page.locator('#btn-comments-load-more')).toBeHidden();
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');
});

/**
 * 수정·답글 폼은 닫혀 있을 때 마운트하지 않는다(v-if) — v-show로 항상 DOM에 두면 최상위 댓글 3개짜리 fixture에서 새 댓글 입력까지 textarea가 7개 나온다.
 */
test('닫힌 댓글 폼은 DOM에 없다가 열었을 때만 생긴다', async ({ page }) => {
    await openOwnPost(page);
    await seedComments(page, 3);
    await page.reload();
    await expect(page.locator('.comment-list__content')).toHaveCount(3);

    // 수정·답글 폼이 공유하는 클래스. 닫힌 상태에서는 하나도 없어야 한다(새 댓글 입력창은 이 클래스를 쓰지 않으므로 포함되지 않는다).
    const formTextareas = page.locator('.comment-edit__textarea');
    await expect(formTextareas).toHaveCount(0);

    await page.locator('.btn-comment-edit').first().click();
    await expect(formTextareas).toHaveCount(1);
    await page.locator('.btn-comment-cancel').first().click();
    await expect(formTextareas).toHaveCount(0);

    await page.locator('.btn-comment-reply').first().click();
    await expect(formTextareas).toHaveCount(1);
    await page.locator('.btn-comment-reply-cancel').first().click();
    await expect(formTextareas).toHaveCount(0);
});
