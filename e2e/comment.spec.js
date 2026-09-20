import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

async function openOwnPost(page) {
    const title = uniqueTitle('댓글');
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('댓글 테스트용 글입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);
}

/** 서버 페이지네이션(PAGE_SIZE=20)을 실제로 넘기기 위해 댓글을 순차로 만든다. */
async function seedComments(page, count) {
    for (let i = 1; i <= count; i += 1) {
        await page.locator('#comment-content').fill(`시드 댓글 ${i}`);
        await page.locator('#btn-comment-save').click();
        await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    }
}

test('빈 댓글은 브라우저 검증이 막고 요청 자체가 나가지 않는다', async ({ page }) => {
    await openOwnPost(page);

    let requested = false;
    page.on('request', (request) => {
        if (request.method() === 'POST' && /\/api\/v1\/posts\/\d+\/comments$/.test(request.url())) {
            requested = true;
        }
    });

    await page.locator('#btn-comment-save').click();

    expect(requested, 'required가 제출 자체를 막는다').toBe(false);
    await expect(page.locator('.comment-list__content')).toHaveCount(0);
});

test('댓글을 등록하면 목록에 나타난다', async ({ page }) => {
    await openOwnPost(page);

    await page.locator('#comment-content').fill('E2E가 남긴 댓글입니다.');
    await page.locator('#btn-comment-save').click();

    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    await expect(page.locator('.comment-list__content')).toContainText('E2E가 남긴 댓글입니다.');
});

/**
 * 댓글 목록의 수정·삭제 버튼은 document에 붙은 위임 핸들러로 동작한다. 위임이 깨지면 버튼이
 * 아무 반응도 하지 않고 조용히 죽으므로, 실제로 눌러 보는 것 말고는 확인할 방법이 없다.
 */
test('댓글을 인라인으로 수정할 수 있다 (위임 핸들러)', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('수정 전 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('수정 전 댓글');

    await page.locator('.btn-comment-edit').first().click();

    const editForm = page.locator('.comment-edit-form').first();
    await expect(editForm).toBeVisible();
    await editForm.locator('textarea').fill('수정 후 댓글');
    await editForm.getByRole('button', { name: '저장' }).click();

    await expect(page.locator('#flash')).toContainText('댓글이 수정되었습니다.');
    await expect(page.locator('.comment-list__content')).toContainText('수정 후 댓글');
});

test('댓글 수정을 취소하면 읽기 상태로 돌아간다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('취소할 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('취소할 댓글');

    await page.locator('.btn-comment-edit').first().click();
    await expect(page.locator('.comment-edit-form').first()).toBeVisible();

    await page.locator('.btn-comment-cancel').first().click();

    await expect(page.locator('.comment-edit-form').first()).toBeHidden();
    await expect(page.locator('.comment-list__content')).toContainText('취소할 댓글');
});

/**
 * 삭제 확인 모달은 게시글과 댓글이 함께 쓴다. 모달이 닫히면 원래 눌렀던 버튼으로 포커스를
 * 되돌리는데, 이것이 깨지면 키보드 사용자가 목록 맨 위로 튕긴다.
 */
/**
 * 취소 버튼을 저장 요청이 끝나기 전에 누를 수 있으면, 화면은 취소로 보이는데 나중에
 * 도착한 응답이 그 내용을 되돌려 놓는 경쟁이 생긴다(개선 보고서 "저장 중 댓글 변경과
 * 동적 삭제 모듈 누락").
 */
test('댓글 저장 중에는 취소할 수 없고, 응답이 오면 저장한 내용이 반영된다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('원본 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('원본 댓글');

    await page.locator('.btn-comment-edit').first().click();
    const editForm = page.locator('.comment-edit-form').first();
    await editForm.locator('textarea').fill('저장 시도 중인 내용');

    let releaseSave;
    const gate = new Promise((resolve) => {
        releaseSave = resolve;
    });
    await page.route(/\/api\/v1\/comments\/\d+$/, async (route) => {
        await gate;
        await route.continue();
    });

    await editForm.getByRole('button', { name: '저장' }).click();

    // 저장 요청이 진행 중인 동안 취소 버튼·입력창이 비활성화되어야 한다.
    await expect(page.locator('.btn-comment-cancel').first()).toBeDisabled();
    await expect(editForm.locator('textarea')).toBeDisabled();

    releaseSave();
    await expect(page.locator('#flash')).toContainText('댓글이 수정되었습니다.');
    await expect(page.locator('.comment-list__content')).toContainText('저장 시도 중인 내용');
});

/**
 * 남의 글에 처음 남기는 댓글은 최초 DOM에 게시글 삭제 버튼도, 기존 댓글도 없다. 삭제 확인
 * 모달 모듈이 그 최초 상태만 보고 로드 여부를 정하면, 방금 낙관적으로 추가된 이 댓글의
 * 삭제 버튼은 위임 핸들러가 없어 눌러도 반응이 없었다.
 */
test('남의 글에 처음 남긴 댓글도 곧바로 지울 수 있다', async ({ page, browser }) => {
    await openOwnPost(page);
    const postUrl = page.url();

    const otherContext = await browser.newContext({ storageState: storageStateFor('other') });
    const otherPage = await otherContext.newPage();
    try {
        await otherPage.goto(postUrl);
        // 남의 글이므로 게시글 삭제 버튼은 없다 — 이 시나리오가 재현하려는 전제 조건이다.
        await expect(otherPage.locator('#btn-delete-post')).toHaveCount(0);

        await otherPage.locator('#comment-content').fill('처음 남기는 댓글입니다.');
        await otherPage.locator('#btn-comment-save').click();
        await expect(otherPage.locator('.comment-list__content')).toContainText('처음 남기는 댓글입니다.');

        await otherPage.locator('.btn-comment-delete').first().click();
        const modal = otherPage.locator('#confirmDeleteModal');
        await expect(modal).toBeVisible();
        await otherPage.locator('#btn-confirm-delete').click();

        await expect(otherPage.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
        await expect(otherPage.locator('.comment-list__content')).toHaveCount(0);
    } finally {
        await otherContext.close();
    }
});

/**
 * 새 댓글을 낙관적으로 더한 뒤 "더 보기"로 다음 페이지를 받으면, 방금 더한 댓글이 서버
 * 페이지에도 다시 포함될 수 있다. id 기준 병합이 없으면 같은 댓글이 두 번 보인다(개선 보고서
 * F01).
 */
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

    // 서버의 마지막 페이지에는 방금 단 댓글(22번째)도 다시 포함되지만, id 기준 병합이
    // 중복을 걸러내야 한다.
    await expect(page.locator('.comment-list__content')).toHaveCount(22);
    await expect(page.locator('#comments-heading')).toContainText('댓글 22개');
});

/**
 * 저장 요청이 진행되는 동안 새 댓글 입력창을 잠가 두지 않으면, 응답이 온 뒤 화면에 붙는
 * 내용이 실제로 보낸 값과 달라질 수 있다(개선 보고서 F02).
 */
test('새 댓글 저장 중에는 입력창이 잠기고, 응답이 오면 보낸 내용이 반영된다', async ({ page }) => {
    await openOwnPost(page);

    let releaseSave;
    const gate = new Promise((resolve) => {
        releaseSave = resolve;
    });
    await page.route(/\/api\/v1\/posts\/\d+\/comments$/, async (route) => {
        if (route.request().method() !== 'POST') {
            await route.continue();
            return;
        }
        await gate;
        await route.continue();
    });

    await page.locator('#comment-content').fill('저장 중 내용');
    await page.locator('#btn-comment-save').click();

    await expect(page.locator('#comment-content')).toBeDisabled();
    await expect(page.locator('#btn-comment-save')).toBeDisabled();

    releaseSave();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    await expect(page.locator('.comment-list__content')).toContainText('저장 중 내용');
    await expect(page.locator('#comment-content')).toHaveValue('');
});

/**
 * http.js의 타임아웃은 fetch가 헤더를 받은 뒤 본문을 다 읽을 때까지도 적용되어야 한다.
 * Playwright의 route.fulfill/continue는 부분 스트리밍 응답을 만들 수 없으므로(본문은 항상
 * 한 번에 완성된 값이어야 한다), 헤더만 즉시 보내고 본문을 끝내지 않는 e2e 전용 엔드포인트
 * (/e2e/slow-body)로 같은 출처(origin) 안에서 리다이렉트해 재현한다(개선 보고서 F03).
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

test('댓글 삭제: 취소하면 그대로, 확인하면 지워진다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('지울 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('지울 댓글');

    await page.locator('.btn-comment-delete').first().click();
    const modal = page.locator('#confirmDeleteModal');
    await expect(modal).toBeVisible();
    await expect(page.locator('#confirmDeleteModalLabel')).toContainText('댓글 삭제');

    await page.locator('#btn-cancel-delete').click();
    await expect(modal).toBeHidden();
    await expect(page.locator('.comment-list__content')).toContainText('지울 댓글');

    // 모달을 닫으면 호출한 버튼으로 포커스가 돌아와야 한다.
    await expect(page.locator('.btn-comment-delete').first()).toBeFocused();

    await page.locator('.btn-comment-delete').first().click();
    await expect(modal).toBeVisible();
    await page.locator('#btn-confirm-delete').click();

    await expect(page.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
    await expect(page.locator('.comment-list__content')).toHaveCount(0);

    // F09: 삭제 성공 경로는 모달이 닫히기 전에 그 댓글의 삭제 버튼(trigger)이 이미 DOM에서
    // 사라진다 — 사라진 요소에 focus()는 조용히 무시되어 포커스가 body로 떨어졌었다. 댓글
    // 영역 제목으로 옮겨가는지 확인한다.
    await expect(page.locator('#comments-heading')).toBeFocused();
});

/**
 * F09: 서버가 내려주는 기존 댓글의 createdAt은 오프셋 없는 LocalDateTime 문자열이고,
 * CommentsApp.vue가 새로 단 댓글에 낙관적으로 채우는 값은 new Date().toISOString()(UTC,
 * 'Z' 포함)이다. CommentItem.vue의 formatDate()는 new Date(iso)로 파싱한 뒤 브라우저 로컬
 * 시간대로 표시하는데, 오프셋 없는 문자열은 브라우저가 "자신의 로컬 시간대"로 해석한다 —
 * 서버가 실제로 그 문자열을 만든 시간대와 브라우저 시간대가 다르면, 같은 댓글이라도 방금 단
 * 직후(클라이언트 값)와 새로고침 후(서버 값)의 표시 시각이 달라질 수 있다.
 * <p>
 * 문서 지시대로 이 동작을 "고치지" 않고 실제로 벌어지는지만 기록한다(임의 UTC 전환 없음).
 * 서버 프로세스의 시간대와 크게 다른 시간대(태평양 Kiritimati, UTC+14)로 브라우저만
 * 강제해, 우연히 같은 시간대라 이 격차가 가려지지 않게 한다.
 */
test('2단계 댓글: 답글을 달면 최상위 댓글 아래 중첩되어 보이고, 답글 자신에는 답글 버튼이 없다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('최상위 댓글입니다.');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    await topLevelItem.locator('.btn-comment-reply').click();
    await topLevelItem.locator('.comment-reply-form textarea').fill('첫 답글입니다.');
    await topLevelItem.locator('.btn-comment-reply-save').click();

    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const replyItem = topLevelItem.locator('.comment-list__replies .comment-list__item');
    await expect(replyItem).toHaveCount(1);
    await expect(replyItem.locator('.comment-list__content')).toContainText('첫 답글입니다.');

    // 3단계(답글의 답글) 금지: 답글 자신에는 "답글" 버튼 자체가 없어야 한다.
    await expect(replyItem.locator('.btn-comment-reply')).toHaveCount(0);
});

test('2단계 댓글: 최상위 댓글을 지우면 그 답글도 함께 사라진다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('삭제될 최상위 댓글입니다.');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    await topLevelItem.locator('.btn-comment-reply').click();
    await topLevelItem.locator('.comment-reply-form textarea').fill('함께 지워질 답글입니다.');
    await topLevelItem.locator('.btn-comment-reply-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    await expect(topLevelItem.locator('.comment-list__replies .comment-list__item')).toHaveCount(1);

    // :scope >로 이 댓글 자신의 삭제 버튼만 골라야 한다 — 중첩된 답글도 같은 클래스의
    // 삭제 버튼을 갖고 있어 단순 후손 선택자로는 둘 다 걸린다.
    await topLevelItem.locator(':scope > .comment-view .btn-comment-delete').click();
    await expect(page.locator('#confirmDeleteModal')).toBeVisible();
    await page.locator('#btn-confirm-delete').click();

    await expect(page.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
    await expect(page.locator('.comment-list__content')).toHaveCount(0);
});

test('F09(검증): 서버 시간대와 다른 브라우저에서는 새로 단 댓글과 새로고침 후 같은 댓글의 표시 시각이 다르다', async ({ browser }) => {
    const context = await browser.newContext({
        storageState: storageStateFor('user'),
        timezoneId: 'Pacific/Kiritimati', // UTC+14 — 서버 프로세스의 실제 시간대와 겹칠 일이 없다.
    });
    const page = await context.newPage();
    await openOwnPost(page);

    await page.locator('#comment-content').fill('시간대 검증용 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const timestamp = page.locator('.comment-list__item .text-muted').first();
    const justPosted = await timestamp.textContent();

    await page.reload();
    const afterReload = await timestamp.textContent();

    // 실제로 격차가 있다는 사실 자체가 이 테스트의 결론이다 — 서버가 만든 오프셋 없는 문자열을
    // 브라우저가 자신의(서버와 다른) 로컬 시간대로 잘못 해석하기 때문이다.
    expect(justPosted, '같은 댓글인데 새로고침 전후 표시 시각이 달라진다(F09, 임의 수정 없이 기록만)')
        .not.toBe(afterReload);

    await context.close();
});

/**
 * F01: "더 보기" 응답이 지연되는 동안 새 댓글을 등록해 totalCount를 로컬에서 22로 올려도,
 * 늦게 도착한 페이지 응답의 totalCount(21)로 되돌아가면 안 된다. mutationSeq가 이 되돌림을
 * 막는다(useRecommendation과 같은 종류의 "응답 순서 뒤집기" 재현).
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

/**
 * F01: 같은 경쟁을 답글 등록으로 재현한다 — 답글도 totalCount를 증감하므로 같은 mutationSeq
 * 가드를 거친다.
 */
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
