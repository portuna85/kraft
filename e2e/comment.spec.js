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

/**
 * F13: 등록·답글에는 required가 있는데 수정 textarea에만 빠져 있어, 내용을 지우고 저장하면
 * 빈 수정 요청이 그대로 서버로 나갔다.
 */
test('댓글 수정에서 내용을 비우고 저장하면 브라우저 검증이 막는다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('비우면 안 되는 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('비우면 안 되는 댓글');

    await page.locator('.btn-comment-edit').first().click();
    const editForm = page.locator('.comment-edit-form').first();
    await expect(editForm).toBeVisible();

    let requested = false;
    page.on('request', (request) => {
        if (request.method() === 'PUT' && /\/api\/v1\/comments\/\d+$/.test(request.url())) {
            requested = true;
        }
    });

    await editForm.locator('textarea').fill('');
    await editForm.getByRole('button', { name: '저장' }).click();

    expect(requested, 'required가 제출 자체를 막는다').toBe(false);
    await expect(editForm).toBeVisible();
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

/**
 * 더 보기 실패는 토스트로도 알리지만 토스트는 지나가면 사라진다. 버튼 자리에 계속 보이는
 * 안내와 재시도 버튼을 함께 제공한다(문서 5.2).
 */
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

test('댓글 삭제: 취소하면 그대로, 확인하면 지워진다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('지울 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('지울 댓글');

    await page.locator('.btn-comment-delete').first().click();
    const modal = page.locator('#confirmDeleteModal');
    await expect(modal).toBeVisible();
    await expect(page.locator('#confirmDeleteModalLabel')).toContainText('댓글 삭제');
    // 삭제 확인 문구에 대상을 명시한다(문서 5.2) — 어느 댓글을 지우는지 모달 안에서 알 수 있다.
    await expect(page.locator('#confirmDeleteMessage')).toContainText('지울 댓글');

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
 * F10: 댓글 A의 삭제 요청이 진행 중일 때 모달을 닫고 댓글 B를 새로 연다. 두 가지를 확인한다 —
 * ①B의 확인 버튼이 A의 disabled 상태에 갇혀 있으면 안 된다(서로 다른 대상이므로 동시에
 * 진행해도 무방하다). ②A의 응답이, 지금 화면에 떠 있는 B의(아직 확인하지 않은) 대화상자를
 * 사용자 모르게 닫아버리면 안 된다 — 실제 삭제 자체는 세대와 무관하게 반영되어야 한다.
 */
test('삭제 A의 늦은 응답이 지금 열려 있는 B의 확인 대화상자를 건드리지 않는다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('먼저 지울 댓글 A');
    await page.locator('#btn-comment-save').click();
    await page.locator('#comment-content').fill('나중에 지울 댓글 B');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toHaveCount(2);

    const commentA = page.locator('.comment-list__item').filter({ hasText: '먼저 지울 댓글 A' });
    const commentB = page.locator('.comment-list__item').filter({ hasText: '나중에 지울 댓글 B' });
    const idA = await commentA.getAttribute('data-comment-id');
    const idB = await commentB.getAttribute('data-comment-id');

    let releaseA;
    const gateA = new Promise((resolve) => {
        releaseA = resolve;
    });
    let releaseB;
    const gateB = new Promise((resolve) => {
        releaseB = resolve;
    });
    await page.route(`**/api/v1/comments/${idA}`, async (route) => {
        if (route.request().method() !== 'DELETE') {
            await route.continue();
            return;
        }
        await gateA;
        await route.continue();
    });
    await page.route(`**/api/v1/comments/${idB}`, async (route) => {
        if (route.request().method() !== 'DELETE') {
            await route.continue();
            return;
        }
        await gateB;
        await route.continue();
    });

    // A를 연다 → 확인(응답은 gateA가 잡아 둔다) → 곧바로 취소로 닫는다.
    await commentA.locator('.btn-comment-delete').click();
    const modal = page.locator('#confirmDeleteModal');
    await expect(modal).toBeVisible();
    await page.locator('#btn-confirm-delete').click();
    await expect(page.locator('#btn-confirm-delete')).toBeDisabled();
    await page.locator('#btn-cancel-delete').click();
    await expect(modal).toBeHidden();

    // B를 새로 연다 — A가 아직 진행 중이어도 확인 버튼이 잠겨 있으면 안 된다.
    await commentB.locator('.btn-comment-delete').click();
    await expect(modal).toBeVisible();
    await expect(page.locator('#btn-confirm-delete')).toBeEnabled();
    await page.locator('#btn-confirm-delete').click();
    await expect(page.locator('#btn-confirm-delete')).toBeDisabled();

    // A의 늦은 응답이 지금 도착한다 — 실제로 지워지긴 해야 하지만, B의 확인이 아직 끝나지
    // 않은 이 대화상자를 사용자 모르게 닫아서는 안 된다.
    releaseA();
    await expect(commentA).toHaveCount(0);
    await expect(modal).toBeVisible();
    await expect(page.locator('#btn-confirm-delete')).toBeDisabled();

    releaseB();
    await expect(page.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
    await expect(modal).toBeHidden();
    await expect(commentB).toHaveCount(0);
    await expect(page.locator('#comments-heading')).toBeFocused();
});

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

/**
 * COR-05 회귀: 서버는 최초 페이지에서 최상위 댓글 하나당 답글을 20개까지만 내려준다. 예전
 * 전역 상한(페이지 전체 500개) 방식은 답글이 많은 부모가 그 상한을 혼자 다 쓰면 나머지가
 * 영원히 숨겨졌다 — 지금은 부모별 상한이라 "답글 더 보기"로 항상 나머지에 도달할 수 있다.
 */
test('COR-05 회귀: 답글이 21개면 새로고침 후 20개만 보이고, 답글 더 보기로 나머지에 도달한다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await page.locator('#comment-content').fill('답글이 많이 달릴 부모 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    for (let i = 1; i <= 21; i += 1) {
        await topLevelItem.locator('.btn-comment-reply').click();
        await topLevelItem.locator('.comment-reply-form textarea').fill(`시드 답글 ${i}`);
        await topLevelItem.locator('.btn-comment-reply-save').click();
        await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    }

    // 로컬에 즉시 반영된 상태(등록 응답을 그대로 붙인 것)라 서버의 부모별 상한과 무관하게
    // 21개 모두 보인다 — 상한은 서버가 처음 페이지를 그릴 때만 적용되므로 새로고침해야 한다.
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
 * 평가 보고서 2026-09-25 F02·F03 회귀: 답글 20개만 받은 상태에서 새 답글을 쓰면, 예전에는
 * "답글 더 보기"가 화면 배열의 마지막 id(새 답글)를 커서로 보내 아직 받지 않은 21번째 답글을
 * 건너뛰었다. 또 답글 삭제가 부모의 답글 수를 줄이지 않아, 이어서 부모를 지우면 개수가 음수가 됐다.
 */
test('F02·F03 회귀: 새 답글을 쓴 뒤 답글 더 보기로 빠짐없이 받고, 답글·부모 삭제 후 개수가 0이다', async ({ page }) => {
    test.slow();
    await openOwnPost(page);
    await page.locator('#comment-content').fill('커서 회귀용 부모 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    for (let i = 1; i <= 21; i += 1) {
        await topLevelItem.locator('.btn-comment-reply').click();
        await topLevelItem.locator('.comment-reply-form textarea').fill(`시드 답글 ${i}`);
        await topLevelItem.locator('.btn-comment-reply-save').click();
        await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    }

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

    await parent.locator(':scope > .comment-view .btn-comment-delete').click();
    await page.locator('#btn-confirm-delete').click();
    await expect(page.locator('#comments-heading')).toContainText('댓글 0개');
});

// 평가 보고서 2026-09-25 F11: 수정 요청은 version이 필수다. 방금 이 화면에서 단 답글도 등록
// 응답의 version을 들고 있어, 새로고침 없이 곧바로 수정할 수 있어야 한다.
test('F11: 방금 단 답글을 새로고침 없이 바로 수정할 수 있다(version 포함)', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('부모 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const parent = page.locator('.comment-list > .comment-list__item').first();
    await parent.locator('.btn-comment-reply').click();
    await parent.locator('.comment-reply-form textarea').fill('수정 전 답글');
    await parent.locator('.btn-comment-reply-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const reply = parent.locator('.comment-list__replies .comment-list__item').first();
    const putBody = page.waitForRequest((r) => r.method() === 'PUT' && /\/api\/v1\/comments\/\d+$/.test(r.url()));
    await reply.locator('.btn-comment-edit').click();
    await reply.locator('.comment-edit__textarea').fill('수정 후 답글');
    await reply.locator('.btn-comment-save').click();

    expect((await putBody).postDataJSON().version).toEqual(expect.any(Number));
    await expect(reply.locator('.comment-list__content')).toHaveText('수정 후 답글');
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

/**
 * COR-08 회귀: 예전에는 서버가 내려주는 기존 댓글의 createdAt이 오프셋 없는 LocalDateTime
 * 문자열이고, CommentsApp.vue가 새로 단 댓글에 낙관적으로 채우는 값은
 * new Date().toISOString()(UTC, 'Z' 포함)이었다. CommentItem.vue의 formatDate()는
 * new Date(iso)로 파싱한 뒤 브라우저 로컬 시간대로 표시하는데, 오프셋 없는 문자열은
 * 브라우저가 "자신의 로컬 시간대"로 해석해 — 서버가 실제로 그 문자열을 만든 시간대와
 * 브라우저 시간대가 다르면, 같은 댓글이라도 방금 단 직후(클라이언트 값)와 새로고침
 * 후(서버 값)의 표시 시각이 달라졌다.
 * <p>
 * 지금은 두 값 모두 서버가 만든 오프셋 있는 CommentViewDto.createdAt에서 나온다(CurrentUser
 * 아님, CommentService.save/update가 저장 직후 엔티티를 다시 읽어 응답에 싣는다) — 새로
 * 단 댓글도 서버 응답을 그대로 반영하고, 직접 시각을 만들지 않는다. 서버 프로세스의
 * 시간대와 크게 다른 시간대(태평양 Kiritimati, UTC+14)로 브라우저만 강제해도 두 표시가
 * 같아야 한다.
 */
test('COR-08 회귀: 서버 시간대와 다른 브라우저에서도 새로 단 댓글과 새로고침 후 같은 댓글의 표시 시각이 같다', async ({ browser }) => {
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

    expect(justPosted, '같은 댓글이면 새로고침 전후로 같은 시각을 보여줘야 한다(COR-08)')
        .toBe(afterReload);

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

/**
 * F09: 수정 취소·저장 성공 모두 폼을 닫지만, 그 순간까지 포커스를 갖고 있던 입력창·저장
 * 버튼은 v-show로 숨겨진다. 트리거였던 "수정" 버튼으로 포커스를 되돌리지 않으면 숨겨진
 * 요소가 활성 요소로 남는다.
 */
test('댓글 수정 취소·저장 후 포커스가 수정 버튼으로 돌아온다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('포커스 검증용 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('포커스 검증용 댓글');

    const editButton = page.locator('.btn-comment-edit').first();
    await editButton.focus();
    await page.keyboard.press('Enter');
    const editForm = page.locator('.comment-edit-form').first();
    await expect(editForm).toBeVisible();
    await expect(editForm.locator('textarea')).toBeFocused();

    await page.locator('.btn-comment-cancel').first().click();
    await expect(editForm).toBeHidden();
    await expect(editButton).toBeFocused();

    await editButton.click();
    await editForm.locator('textarea').fill('수정 후 포커스 확인');
    await editForm.getByRole('button', { name: '저장' }).click();
    await expect(page.locator('#flash')).toContainText('댓글이 수정되었습니다.');
    await expect(editForm).toBeHidden();
    await expect(editButton).toBeFocused();
});

/** F09: 답글도 같은 규칙 — 답글 버튼에서 시작해 취소·등록 후 그 버튼으로 돌아온다. */
test('답글 취소·등록 후 포커스가 답글 버튼으로 돌아온다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('답글 포커스 검증용 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('답글 포커스 검증용 댓글');

    const replyButton = page.locator('.btn-comment-reply').first();
    await replyButton.focus();
    await page.keyboard.press('Enter');
    const replyForm = page.locator('.comment-reply-form').first();
    await expect(replyForm).toBeVisible();
    await expect(replyForm.locator('textarea')).toBeFocused();

    await page.locator('.btn-comment-reply-cancel').first().click();
    await expect(replyForm).toBeHidden();
    await expect(replyButton).toBeFocused();

    await replyButton.click();
    await replyForm.locator('textarea').fill('포커스 확인용 답글');
    await replyForm.getByRole('button', { name: '답글 등록' }).click();
    await expect(page.locator('.comment-list__replies')).toContainText('포커스 확인용 답글');
    await expect(replyForm).toBeHidden();
    await expect(replyButton).toBeFocused();
});

/**
 * F11: 각 최상위 댓글은 열림 여부와 무관하게 수정·답글 폼을 항상 DOM에 유지했다(v-show).
 * 최상위 댓글 3개짜리 fixture에서 새 댓글 입력까지 textarea 7개가 나온 것이 이 때문이다.
 * v-if로 바꿔 닫혀 있을 때는 아예 마운트하지 않는지 확인한다.
 */
test('닫힌 댓글 폼은 DOM에 없다가 열었을 때만 생긴다', async ({ page }) => {
    await openOwnPost(page);
    await seedComments(page, 3);
    await expect(page.locator('.comment-list__content')).toHaveCount(3);

    // 수정·답글 폼이 공유하는 클래스. 닫힌 상태에서는 하나도 없어야 한다(새 댓글 입력창은
    // 이 클래스를 쓰지 않으므로 포함되지 않는다).
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
