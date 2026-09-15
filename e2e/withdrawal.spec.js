import { test, expect, PASSWORD, login, uniqueTitle } from './fixtures.js';

// 계정을 실제로 없애므로 시드 계정을 쓰지 않는다. 매번 이 스펙 전용 계정을 만든다.
test.use({ storageState: { cookies: [], origins: [] } });

async function signUpAndLogin(page, email) {
    await page.goto('/signup');
    await page.locator('#name').fill(uniqueTitle('탈퇴').slice(0, 20));
    await page.locator('#email').fill(email);
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(PASSWORD);
    await page.locator('#btn-signup').click();
    await page.waitForURL(/\/login/);
    await login(page, email);
}

async function openWithdrawModal(page) {
    await page.goto('/');
    await page.locator('#btn-withdraw').click();
    await expect(page.locator('#withdrawModal')).toBeVisible();
}

test('탈퇴하면 로그아웃되고 그 계정으로는 다시 로그인할 수 없다', async ({ page }) => {
    const email = `${uniqueTitle('bye').toLowerCase()}@e2e.test`;
    await signUpAndLogin(page, email);

    await openWithdrawModal(page);
    await page.locator('#withdrawPassword').fill(PASSWORD);
    await page.locator('#btn-confirm-withdraw').click();

    await page.waitForURL(/\/login/);
    await expect(page.locator('#flash')).toContainText('탈퇴가 완료되었습니다');

    // 세션은 서버가 이미 폐기했고, 계정도 로그인할 수 없는 상태다.
    await page.goto('/login');
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(PASSWORD);
    await page.getByRole('button', { name: '로그인' }).click();
    await expect(page.locator('.alert-danger')).toContainText('올바르지 않습니다');

    // 같은 주소로 다시 가입할 수 있다 — 탈퇴가 그 주소를 영영 잠그면 안 된다.
    await page.goto('/signup');
    await page.locator('#name').fill(uniqueTitle('복귀').slice(0, 20));
    await page.locator('#email').fill(email);
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(PASSWORD);
    await page.locator('#btn-signup').click();
    await expect(page).toHaveURL(/\/login/);
    await expect(page.locator('#flash')).toContainText('가입이 완료되었습니다');
});

test('비밀번호가 틀리면 모달 안에서 알려주고 계정은 그대로다', async ({ page }) => {
    const email = `${uniqueTitle('keep').toLowerCase()}@e2e.test`;
    await signUpAndLogin(page, email);

    await openWithdrawModal(page);
    await page.locator('#withdrawPassword').fill('WrongPass1!');
    await page.locator('#btn-confirm-withdraw').click();

    // 화면 이동이 없으므로 flash가 아니라 모달 안에서 보여준다.
    await expect(page.locator('#withdraw-error')).toBeVisible();
    await expect(page.locator('#withdrawModal')).toBeVisible();

    // 계정이 살아 있어야 한다 — 다시 로그인해 확인한다.
    await page.goto('/');
    await login(page, email);
    await expect(page.locator('.kraft-actions__name')).toBeVisible();
});

test('탈퇴해도 쓴 글은 목록에 남고 작성자만 익명으로 바뀐다', async ({ page, request }) => {
    const email = `${uniqueTitle('post').toLowerCase()}@e2e.test`;
    const title = uniqueTitle('탈퇴후에도남을글');
    await signUpAndLogin(page, email);

    // 글을 쓰려면 이메일 인증을 마쳐야 한다. 발송된 메일에서 링크를 꺼내 밟는다.
    const mailUrl = `/e2e/mails/latest?to=${encodeURIComponent(email)}`;
    await expect.poll(async () => (await request.get(mailUrl)).status(), { timeout: 10_000 }).toBe(200);
    const mail = await (await request.get(mailUrl)).json();
    await page.goto(mail.text.match(/https?:\/\/\S+/)[0]);
    await login(page, email); // 권한은 다시 로그인해야 반영된다.

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill('작성자가 탈퇴해도 이 글은 남아야 한다.');
    await page.locator('#btn-save').click();
    await page.waitForURL('/');

    await openWithdrawModal(page);
    // 모달의 안내와 실제 동작이 같은지가 이 테스트의 핵심이다.
    await expect(page.locator('#withdrawModal')).toContainText('글과 댓글은 남고');
    await page.locator('#withdrawPassword').fill(PASSWORD);
    await page.locator('#btn-confirm-withdraw').click();
    await page.waitForURL(/\/login/);

    // 로그인하지 않은 방문자에게도 글은 그대로 보이고, 작성자만 익명이다.
    await page.goto(`/?q=${encodeURIComponent(title)}`);
    const row = page.locator('.post-list__item').filter({ hasText: title });
    await expect(row).toBeVisible();
    await expect(row.locator('.post-list__author')).toContainText('탈퇴한 사용자');
});
