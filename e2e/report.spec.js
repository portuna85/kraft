import { test, expect, ACCOUNTS, login, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

/**
 * 신고부터 관리자 처리까지 한 바퀴를 돈다. 두 사람이 필요하므로 저장해 둔 로그인 상태를
 * 번갈아 쓴다: user가 글을 쓰고, other가 신고하고, admin이 처리한다.
 */

async function writePost(page, title) {
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('신고 시나리오용 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
}

async function reportOpenPost(page, reason, detail) {
    await page.locator('#btn-report-post').click();
    await expect(page.locator('#reportModal')).toBeVisible();
    await page.locator('#report-reason').selectOption(reason);
    if (detail) {
        await page.locator('#report-detail').fill(detail);
    }
    await page.locator('#btn-confirm-report').click();
}

test.describe('신고 접수', () => {
    // 기본 page 픽스처는 글 작성자(user)로 로그인한 상태를 쓴다. 신고자·관리자가 필요한
    // 테스트는 그 안에서 별도 컨텍스트를 연다.
    test.use({ storageState: storageStateFor('user') });

    test('남의 글을 신고하면 접수되고, 같은 글을 다시 신고하면 거절한다', async ({ browser }) => {
        const title = uniqueTitle('신고대상');

        const authorPage = await (await browser.newContext({ storageState: storageStateFor('user') })).newPage();
        await writePost(authorPage, title);
        await authorPage.close();

        const reporterPage = await (await browser.newContext({ storageState: storageStateFor('other') })).newPage();
        await openPostByTitle(reporterPage, title);
        await reportOpenPost(reporterPage, 'SPAM', '같은 글을 반복해 올립니다.');
        await expect(reporterPage.locator('#app-toast')).toContainText('신고가 접수되었습니다');

        // 두 번째 신고는 목록만 부풀린다. 서버가 막고, 사용자는 이유를 안다.
        await reporterPage.reload();
        await reportOpenPost(reporterPage, 'SPAM', null);
        await expect(reporterPage.locator('#app-toast')).toContainText('이미 신고한 대상');
        await reporterPage.close();
    });

    test('자기 글에는 신고 버튼이 보이지 않는다', async ({ page }) => {
        // 자기 글은 직접 지우면 되므로 서버도 거절한다. 버튼부터 보이지 않는 편이 낫다.
        const title = uniqueTitle('내글');
        await writePost(page, title);
        await openPostByTitle(page, title);

        await expect(page.locator('#btn-edit')).toBeVisible();
        await expect(page.locator('#btn-report-post')).toHaveCount(0);
    });

});

test.describe('관리자 처리', () => {
    test('신고를 처리하면 대상 글이 사라지고 목록에서도 빠진다', async ({ browser }) => {
        const title = uniqueTitle('삭제될글');

        const authorPage = await (await browser.newContext({ storageState: storageStateFor('user') })).newPage();
        await writePost(authorPage, title);
        await authorPage.close();

        const reporterPage = await (await browser.newContext({ storageState: storageStateFor('other') })).newPage();
        await openPostByTitle(reporterPage, title);
        await reportOpenPost(reporterPage, 'ABUSE', '욕설이 있습니다.');
        await expect(reporterPage.locator('#app-toast')).toContainText('신고가 접수되었습니다');
        await reporterPage.close();

        // 저장해 둔 admin 로그인 상태를 쓰지 않는다 — 비밀번호 변경 스펙이 admin의 모든 세션을
        // 폐기하므로, 실행 순서에 따라 그 상태가 이미 죽어 있을 수 있다. 여기서 새로 로그인한다.
        const adminPage = await (await browser.newContext()).newPage();
        await login(adminPage, ACCOUNTS.admin.email);
        await adminPage.goto('/admin/reports');
        const row = adminPage.locator('.report-list__item').filter({ hasText: title });
        await expect(row).toBeVisible();

        await row.locator('.btn-report-resolve').click();
        // 처리에 성공하면 현재 페이지를 다시 불러온다(F05) — 로컬에서 줄만 지우면 "처리 대기"
        // 카운트·페이지 수가 서버 상태와 어긋날 수 있다. 재요청·재조회가 끝날 때까지
        // toHaveCount가 재시도하며 기다린다.
        await expect(adminPage.locator('.report-list__item').filter({ hasText: title })).toHaveCount(0);

        // 글도 실제로 사라졌다.
        await adminPage.goto(`/?q=${encodeURIComponent(title)}`);
        await expect(adminPage.locator('.post-list__item').filter({ hasText: title })).toHaveCount(0);
        await adminPage.close();
    });

    test('F05: 처리 요청이 도는 동안 같은 줄의 다른 처리 버튼도 비활성 상태다', async ({ browser }) => {
        const title = uniqueTitle('이중클릭방지');

        const authorPage = await (await browser.newContext({ storageState: storageStateFor('user') })).newPage();
        await writePost(authorPage, title);
        await authorPage.close();

        const reporterPage = await (await browser.newContext({ storageState: storageStateFor('other') })).newPage();
        await openPostByTitle(reporterPage, title);
        await reportOpenPost(reporterPage, 'SPAM', '이중 클릭 방지 확인용');
        await expect(reporterPage.locator('#app-toast')).toContainText('신고가 접수되었습니다');
        await reporterPage.close();

        const adminPage = await (await browser.newContext()).newPage();
        await login(adminPage, ACCOUNTS.admin.email);
        await adminPage.goto('/admin/reports');
        const row = adminPage.locator('.report-list__item').filter({ hasText: title });

        let releaseResponse;
        const held = new Promise((resolve) => {
            releaseResponse = resolve;
        });
        await adminPage.route('**/api/v1/admin/reports/*/resolve*', async (route) => {
            await held;
            await route.continue();
        });

        await row.locator('.btn-report-resolve').click();

        // 삭제 요청이 도는 동안 같은 줄의 반려 버튼도 잠겨야 한다 — 그렇지 않으면 삭제와
        // 반려를 동시에 눌러 서로 다른 처리가 겹치는 경쟁이 생긴다.
        await expect(row.locator('.btn-report-resolve')).toBeDisabled();
        await expect(row.locator('.btn-report-reject')).toBeDisabled();

        releaseResponse();
        await expect(adminPage.locator('.report-list__item').filter({ hasText: title })).toHaveCount(0);
        await adminPage.close();
    });

    test('관리자가 아니면 신고 화면에 들어갈 수 없다', async ({ browser }) => {
        const page = await (await browser.newContext({ storageState: storageStateFor('user') })).newPage();

        const response = await page.goto('/admin/reports');

        expect(response.status(), '일반 회원에게는 403이다').toBe(403);
        await page.close();
    });
});
