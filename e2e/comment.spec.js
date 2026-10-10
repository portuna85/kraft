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

/** 수정·삭제 버튼은 document에 붙은 위임 핸들러로 동작한다. 위임이 깨지면 버튼이 조용히 죽으므로 실제로 눌러 보는 것 말고는 확인할 방법이 없다. */
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

/** 수정 textarea에도 required가 있어야 한다 — 없으면 내용을 지우고 저장할 때 빈 수정 요청이 서버로 나간다. */
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

/** 취소 버튼을 저장 요청이 끝나기 전에 누를 수 있으면, 화면은 취소로 보이는데 나중에 도착한 응답이 그 내용을 되돌려 놓는 경쟁이 생긴다. */
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

/** 남의 글에 처음 남기는 댓글은 최초 DOM에 게시글 삭제 버튼도 기존 댓글도 없다. 삭제 확인 모달 모듈이 최초 상태만 보고 로드 여부를 정하면, 방금 추가된 이 댓글의 삭제 버튼은 위임 핸들러가 없어 눌러도 반응이 없다. */
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

/** 저장 요청이 진행되는 동안 새 댓글 입력창을 잠가 두지 않으면, 응답이 온 뒤 화면에 붙는 내용이 실제로 보낸 값과 달라질 수 있다. */
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

test('댓글 삭제: 취소하면 그대로, 확인하면 지워진다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('지울 댓글');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText('지울 댓글');

    await page.locator('.btn-comment-delete').first().click();
    const modal = page.locator('#confirmDeleteModal');
    await expect(modal).toBeVisible();
    await expect(page.locator('#confirmDeleteModalLabel')).toContainText('댓글 삭제');
    // 삭제 확인 문구에 대상을 명시한다 — 어느 댓글을 지우는지 모달 안에서 알 수 있다.
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

    // 삭제 성공 경로는 모달이 닫히기 전에 그 댓글의 삭제 버튼(trigger)이 이미 DOM에서 사라져 focus()가 무시된다 — 댓글 영역 제목으로 옮겨가는지 확인한다.
    await expect(page.locator('#comments-heading')).toBeFocused();
});

/**
 * 댓글 A의 삭제 요청이 진행 중일 때 모달을 닫고 댓글 B를 새로 연다. 두 가지를 확인한다 — ①B의 확인 버튼이 A의 disabled 상태에 갇혀 있으면 안 된다(서로 다른 대상이므로 동시에 진행해도 무방하다).
 * ②A의 응답이, 지금 화면에 떠 있는 B의(아직 확인하지 않은) 대화상자를 사용자 모르게 닫아버리면 안 된다 — 실제 삭제 자체는 세대와 무관하게 반영되어야 한다.
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

    // A의 늦은 응답이 지금 도착한다 — 실제로 지워지긴 해야 하지만, B의 확인이 아직 끝나지 않은 이 대화상자를 사용자 모르게 닫아서는 안 된다.
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

// 수정 요청은 기준 버전(If-Match)이 필수다. 방금 이 화면에서 단 답글도 등록 응답의 version을 들고 있어, 새로고침 없이 곧바로 수정할 수 있어야 한다.
test('방금 단 답글을 새로고침 없이 바로 수정할 수 있다(If-Match 포함)', async ({ page }) => {
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

    const request = await putBody;
    // 기준 버전은 If-Match 헤더로만 보낸다 — 본문에는 version이 없다.
    expect(request.headers()['if-match']).toMatch(/^"\d+"$/);
    expect(request.postDataJSON()).not.toHaveProperty('version');
    await expect(reply.locator('.comment-list__content')).toHaveText('수정 후 답글');
});

/**
 * 답글이 있는 최상위 댓글을 지우면 행을 지우지 않고 내용만 "삭제된 댓글입니다"로 바꾸며 답글은 그대로 남는다(최상위 댓글 작성자 한 사람의 선택이 남이 쓴 답글까지 지우지 않게).
 */
test('답글이 있는 최상위 댓글을 지우면 행은 남고 답글은 살아남는다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('삭제될 최상위 댓글입니다.');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    await topLevelItem.locator('.btn-comment-reply').click();
    await topLevelItem.locator('.comment-reply-form textarea').fill('살아남을 답글입니다.');
    await topLevelItem.locator('.btn-comment-reply-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');
    await expect(topLevelItem.locator('.comment-list__replies .comment-list__item')).toHaveCount(1);

    // :scope >로 이 댓글 자신의 삭제 버튼만 고른다 — 중첩된 답글도 같은 클래스의 삭제 버튼을 가져 단순 후손 선택자로는 둘 다 걸린다.
    await topLevelItem.locator(':scope > .comment-view .btn-comment-delete').click();
    await expect(page.locator('#confirmDeleteModal')).toBeVisible();
    await page.locator('#btn-confirm-delete').click();

    await expect(page.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
    // 행은 지워지지 않고 내용만 바뀐다 — 수정·삭제 버튼은 사라지고 답글은 그대로다.
    await expect(topLevelItem.locator(':scope > .comment-view .comment-list__content'))
        .toContainText('삭제된 댓글입니다');
    await expect(topLevelItem.locator('.comment-list__replies .comment-list__item')).toHaveCount(1);
    await expect(topLevelItem.locator(':scope > .comment-view .btn-comment-edit')).toHaveCount(0);
    await expect(topLevelItem.locator(':scope > .comment-view .btn-comment-delete')).toHaveCount(0);
});

/** 답글이 없는 최상위 댓글은 지금까지처럼 행 자체가 사라진다. */
test('답글이 없는 최상위 댓글을 지우면 지금처럼 행 자체가 사라진다', async ({ page }) => {
    await openOwnPost(page);
    await page.locator('#comment-content').fill('답글 없이 지워질 댓글입니다.');
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('#flash')).toContainText('댓글이 등록되었습니다.');

    const topLevelItem = page.locator('.comment-list > .comment-list__item').first();
    await topLevelItem.locator(':scope > .comment-view .btn-comment-delete').click();
    await page.locator('#btn-confirm-delete').click();

    await expect(page.locator('#flash')).toContainText('댓글이 삭제되었습니다.');
    await expect(page.locator('.comment-list__content')).toHaveCount(0);
});

/**
 * 회귀: 새로 단 댓글의 시각을 클라이언트가 직접 만들면(예: new Date().toISOString()) 서버가 내려주는 값과 시간대 해석이 달라, 같은 댓글인데 방금 단 직후와 새로고침 후의 표시 시각이 달라진다.
 * 지금은 두 값 모두 서버 응답(CommentService.save/update가 저장 직후 엔티티를 다시 읽어 싣는다)에서 나온다. 서버 시간대와 크게 다른 시간대(Pacific/Kiritimati, UTC+14)로 브라우저만 강제해도 두 표시가 같아야 한다.
 */
test('회귀: 서버 시간대와 다른 브라우저에서도 새로 단 댓글과 새로고침 후 같은 댓글의 표시 시각이 같다', async ({ browser }) => {
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

    expect(justPosted, '같은 댓글이면 새로고침 전후로 같은 시각을 보여줘야 한다')
        .toBe(afterReload);

    await context.close();
});

/**
 * 수정 취소·저장 성공 모두 폼을 닫지만, 그 순간까지 포커스를 갖고 있던 입력창·저장 버튼은 숨겨진다. 트리거였던 "수정" 버튼으로 포커스를 되돌리지 않으면 숨겨진 요소가 활성 요소로 남는다.
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

/** 답글도 같은 규칙 — 답글 버튼에서 시작해 취소·등록 후 그 버튼으로 돌아온다. */
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
