import { test, expect, PASSWORD, login, openAccountMenu, uniqueTitle } from './fixtures.js';

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
    await openAccountMenu(page);
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

/**
 * F09: 완료 안내가 폼을 대체하지만 명시적인 상태 알림이나 포커스 이동이 없어, 스크린리더
 * 사용자가 요청이 끝났다는 사실을 놓칠 수 있었다.
 */
test('링크 요청 후 완료 안내로 포커스가 옮겨가고 상태로 알려진다', async ({ page }) => {
    await page.goto('/forgot-password');

    await page.locator('#email').fill('nobody-here@e2e.test');
    await page.locator('#btn-forgot-password').click();

    const done = page.locator('#forgot-password-done');
    await expect(done).toHaveAttribute('role', 'status');
    await expect(done.locator('p').first()).toBeFocused();
});

/**
 * F13: 이 화면은 성공해도 페이지 이동이 없다 — 실패 후 재시도해 성공하면, 이전 시도가 남긴
 * #flash 오류 배너가 완료 안내와 함께 남아 있었다.
 */
test('실패 후 재시도에 성공하면 이전 오류 배너가 완료 안내와 함께 남지 않는다', async ({ page }) => {
    let requestCount = 0;
    await page.route('**/api/v1/users/password-reset', async (route) => {
        requestCount += 1;
        if (requestCount === 1) {
            await route.fulfill({ status: 500, contentType: 'application/json', body: '{"detail":"일시적인 오류"}' });
            return;
        }
        await route.fulfill({ status: 204 });
    });

    await page.goto('/forgot-password');
    await page.locator('#email').fill('nobody-here@e2e.test');
    await page.locator('#btn-forgot-password').click();
    await expect(page.locator('#flash')).toContainText('일시적인 오류');

    await page.locator('#btn-forgot-password').click();

    await expect(page.locator('#forgot-password-done')).toContainText('가입된 주소라면 재설정 링크를 보냈습니다.');
    await expect(page.locator('#flash')).toBeHidden();
});

test('만료되거나 없는 링크는 다시 요청하라고 알려준다', async ({ page }) => {
    await page.goto('/users/password-reset?token=this-token-does-not-exist');

    await page.locator('#newPassword').fill('Another1!pass');
    await page.locator('#newPasswordConfirm').fill('Another1!pass');
    await page.locator('#btn-password-reset').click();

    await expect(page.locator('#flash')).toContainText('다시 요청해 주세요');
});

/**
 * F13: SignupApp.vue와 같은 문제 — 확인란을 고쳐 다시 일치시켜도 재제출 전까지 오류가 남았다.
 */
test('비밀번호 확인을 고쳐 다시 일치시키면 재제출 전에도 오류가 사라진다', async ({ page }) => {
    await page.goto('/users/password-reset?token=this-token-does-not-exist');

    await page.locator('#newPassword').fill('Another1!pass');
    await page.locator('#newPasswordConfirm').fill('Different1!pass');
    await page.locator('#btn-password-reset').click();
    await expect(page.locator('#newPasswordConfirm-error')).toBeVisible();

    await page.locator('#newPasswordConfirm').fill('Another1!pass');

    await expect(page.locator('#newPasswordConfirm-error')).toBeEmpty();
    await expect(page.locator('#newPasswordConfirm')).not.toHaveAttribute('aria-invalid');
});
