import { test, expect, ACCOUNTS, PASSWORD, login, storageStateFor, uniqueTitle } from './fixtures.js';

// 정지는 계정 상태를 바꾼다. 시드 계정을 쓰면 뒤에 도는 다른 스펙이 글을 못 쓰게 되므로
// (인메모리 DB를 모두 공유한다) 이 스펙만의 계정을 매번 새로 만든다.
test.use({ storageState: { cookies: [], origins: [] } });

/** 가입 → 메일 인증 → 로그인까지. 글을 쓰려면 인증을 마쳐야 한다. */
async function signUpVerifiedAndLogin(page, request, email) {
    const name = uniqueTitle('정지').slice(0, 20);
    await page.goto('/signup');
    await page.locator('#name').fill(name);
    await page.locator('#email').fill(email);
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(PASSWORD);
    await page.locator('#btn-signup').click();
    await page.waitForURL(/\/login/);

    const mailUrl = `/e2e/mails/latest?to=${encodeURIComponent(email)}`;
    await expect.poll(async () => (await request.get(mailUrl)).status(), { timeout: 10_000 }).toBe(200);
    const mail = await (await request.get(mailUrl)).json();
    await page.goto(mail.text.match(/https?:\/\/\S+/)[0]);

    await login(page, email); // 권한은 다시 로그인해야 반영된다.
    return name;
}

test('신고를 정지와 함께 처리하면 그 사람은 글을 쓸 수 없고 이유를 본다', async ({ page, request, browser }) => {
    const email = `${uniqueTitle('bad').toLowerCase()}@e2e.test`;
    const title = uniqueTitle('정지대상글');

    const name = await signUpVerifiedAndLogin(page, request, email);
    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('신고와 정지 시나리오용 본문입니다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');

    // 다른 사람이 신고한다.
    const reporterPage = await (await browser.newContext({ storageState: storageStateFor('other') })).newPage();
    await reporterPage.goto(`/?q=${encodeURIComponent(title)}`);
    await reporterPage.locator('.post-list__title').filter({ hasText: title }).first().click();
    await reporterPage.locator('#btn-report-post').click();
    await reporterPage.locator('#report-reason').selectOption('ABUSE');
    await reporterPage.locator('#btn-confirm-report').click();
    await expect(reporterPage.locator('#app-toast')).toContainText('신고가 접수되었습니다');
    await reporterPage.close();

    // 관리자가 삭제하면서 작성자를 정지한다. (저장된 admin 상태는 비밀번호 변경 스펙이
    // 폐기할 수 있어 새로 로그인한다.)
    const adminPage = await (await browser.newContext()).newPage();
    await login(adminPage, ACCOUNTS.admin.email);
    await adminPage.goto('/admin/reports');
    const row = adminPage.locator('.report-list__item').filter({ hasText: title });
    // 삭제+정지는 되돌릴 수 없어 확인 대화상자를 한 번 더 거친다(개선 보고서 SEC-05).
    await row.locator('.btn-report-suspend').click();
    await expect(adminPage.locator('#confirmDeleteModal')).toBeVisible();
    await adminPage.locator('#btn-confirm-delete').click();
    // 처리에 성공하면 현재 페이지를 다시 불러온다(F05) — 그 줄이 목록에서 빠지는 것으로 확인한다.
    await expect(adminPage.locator('.report-list__item').filter({ hasText: title })).toHaveCount(0);
    await adminPage.close();

    // 정지된 사람은 글쓰기 폼 대신 이유를 본다. 읽기와 로그인은 그대로다.
    await page.goto('/posts/save');
    await expect(page.getByText('이용이 제한된 계정입니다')).toBeVisible();
    await expect(page.locator('#post-save-app')).toHaveCount(0);

    // 댓글도 같은 규칙으로 막히고, 같은 이유를 보여준다.
    await page.goto('/');
    await page.locator('.post-list__title').first().click();
    await expect(page.locator('.comments__login-hint')).toContainText('이용이 제한된 계정입니다');
    await expect(page.locator('#comment-content')).toHaveCount(0);

    // 관리자가 기간 전에 풀면 곧바로 다시 쓸 수 있다. 정지는 사람의 판단이라 되돌릴 길이 있어야 한다.
    const adminAgain = await (await browser.newContext()).newPage();
    await login(adminAgain, ACCOUNTS.admin.email);
    await adminAgain.goto('/admin/users');
    const userRow = adminAgain.locator('.report-list__item').filter({ hasText: name });
    await expect(userRow).toContainText('욕설·비방');
    await userRow.locator('.btn-lift-suspension').click();
    // 처리에 성공하면 현재 페이지를 다시 불러온다(F05). 푼 줄이 목록에서 빠지는 것으로 확인한다.
    await expect(adminAgain.locator('.report-list__item').filter({ hasText: name })).toHaveCount(0);
    await adminAgain.close();

    await page.goto('/posts/save');
    await expect(page.locator('#post-save-app')).toBeVisible();
});
