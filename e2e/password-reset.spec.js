import { test, expect, PASSWORD, login, uniqueTitle } from './fixtures.js';

// 비밀번호를 잊은 사람의 흐름이므로 로그인 상태 없이 돈다.
test.use({ storageState: { cookies: [], origins: [] } });

function newEmail() {
    return `${uniqueTitle('reset').toLowerCase()}@e2e.test`;
}

/** 이 스펙 전용 계정을 하나 만든다. 비밀번호를 실제로 바꾸므로 시드 계정을 쓰면 안 된다. */
async function signUp(page, email) {
    await page.goto('/signup');
    await page.locator('#name').fill(uniqueTitle('찾기').slice(0, 20));
    await page.locator('#email').fill(email);
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(PASSWORD);
    await page.locator('#btn-signup').click();
    await page.waitForURL(/\/login/);
}

/** 발송된 메일에서 링크를 꺼낸다. 메일은 트랜잭션 밖에서 비동기로 나가므로 잠깐 기다린다. */
async function latestMailTo(request, email) {
    const url = `/e2e/mails/latest?to=${encodeURIComponent(email)}`;
    await expect
        .poll(async () => (await request.get(url)).status(), { timeout: 10_000 })
        .toBe(200);
    return (await request.get(url)).json();
}

test('메일 링크로 새 비밀번호를 정하고 그 비밀번호로 로그인한다', async ({ page, request }) => {
    const email = newEmail();
    const newPassword = `Reset${uniqueTitle('p').slice(-6)}!aA1`;
    await signUp(page, email);

    // 로그인 화면에서 시작한다 — 실제 사용자가 들어오는 길이다.
    await page.goto('/login');
    await page.locator('#link-forgot-password').click();
    await page.waitForURL('/forgot-password');

    await page.locator('#email').fill(email);
    await page.locator('#btn-forgot-password').click();
    await expect(page.locator('#forgot-password-done')).toContainText('가입된 주소라면');

    const mail = await latestMailTo(request, email);
    expect(mail.subject).toContain('비밀번호 재설정');
    const resetUrl = mail.text.match(/https?:\/\/\S+/)[0];

    await page.goto(resetUrl);
    await page.locator('#newPassword').fill(newPassword);
    await page.locator('#newPasswordConfirm').fill(newPassword);
    await page.locator('#btn-password-reset').click();

    await page.waitForURL(/\/login/);
    await expect(page.locator('#flash')).toContainText('새 비밀번호로 변경되었습니다');

    // 옛 비밀번호로는 못 들어간다.
    await page.goto('/login');
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(PASSWORD);
    await page.getByRole('button', { name: '로그인' }).click();
    await expect(page.locator('.alert-danger')).toContainText('올바르지 않습니다');

    // 새 비밀번호로는 들어간다.
    await login(page, email, newPassword);
    await expect(page.locator('.kraft-actions__name')).toBeVisible();

    // 링크는 1회용이다. 메일함에 남은 같은 링크를 다시 열어도 통하지 않는다.
    await page.goto(resetUrl);
    await page.locator('#newPassword').fill(`${newPassword}x`);
    await page.locator('#newPasswordConfirm').fill(`${newPassword}x`);
    await page.locator('#btn-password-reset').click();
    await expect(page.locator('#flash')).toContainText('유효하지 않은 재설정 링크');
});

test('가입되지 않은 주소도 같은 안내를 보여준다 — 응답으로 가입 여부를 알 수 없다', async ({ page }) => {
    await page.goto('/forgot-password');

    await page.locator('#email').fill('nobody-here@e2e.test');
    await page.locator('#btn-forgot-password').click();

    // 가입된 주소로 요청했을 때와 글자 하나까지 같아야 한다.
    await expect(page.locator('#forgot-password-done')).toContainText('가입된 주소라면 재설정 링크를 보냈습니다.');
});

test('만료되거나 없는 링크는 다시 요청하라고 알려준다', async ({ page }) => {
    await page.goto('/users/password-reset?token=this-token-does-not-exist');

    await page.locator('#newPassword').fill('Another1!pass');
    await page.locator('#newPasswordConfirm').fill('Another1!pass');
    await page.locator('#btn-password-reset').click();

    await expect(page.locator('#flash')).toContainText('다시 요청해 주세요');
});
