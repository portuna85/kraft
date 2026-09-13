import { test, expect, PASSWORD, login, uniqueTitle } from './fixtures.js';

// 가입부터 시작하므로 로그인 상태 없이 돈다.
test.use({ storageState: { cookies: [], origins: [] } });

function newEmail() {
    return `${uniqueTitle('signup').toLowerCase()}@e2e.test`;
}

test('비밀번호 확인이 다르면 필드 옆에서 알려주고 요청을 보내지 않는다', async ({ page }) => {
    let requested = false;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/users')) {
            requested = true;
        }
    });

    await page.goto('/signup');
    await page.locator('#name').fill('불일치');
    await page.locator('#email').fill(newEmail());
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(`${PASSWORD}xx`);
    await page.locator('#btn-signup').click();

    await expect(page.locator('#passwordConfirm-error')).toBeVisible();
    expect(requested, '서버까지 갈 필요가 없다').toBe(false);
});

test('이미 가입된 이메일이면 서버 메시지를 그대로 보여준다', async ({ page }) => {
    await page.goto('/signup');
    await page.locator('#name').fill(uniqueTitle('중복'));
    await page.locator('#email').fill('user@e2e.test');
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(PASSWORD);
    await page.locator('#btn-signup').click();

    await expect(page.locator('#flash')).toContainText('이미 가입된 이메일입니다');
});

/**
 * 가입 → 인증 메일의 링크를 열어 승격 → 글쓰기 가능까지 한 번에 확인한다.
 *
 * 메일을 실제로 보내지 않고 RecordingEmailSender가 담아두므로, E2E 전용 엔드포인트에서
 * 본문을 읽어 링크를 꺼낸다. 이 장치가 없으면 자동화할 방법이 없는 흐름이다.
 */
test('가입하고 인증 링크를 열면 글을 쓸 수 있게 된다', async ({ page, request }) => {
    const email = newEmail();
    const name = uniqueTitle('신규');

    await page.goto('/signup');
    await page.locator('#name').fill(name);
    await page.locator('#email').fill(email);
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(PASSWORD);
    await page.locator('#btn-signup').click();

    await page.waitForURL(/\/login/);
    await expect(page.locator('#flash')).toContainText('가입이 완료되었습니다');

    // 인증 전에는 GUEST라 글쓰기 폼 대신 안내가 보인다.
    await login(page, email);
    await page.goto('/posts/save');
    await expect(page.getByText('이메일 인증을 완료해야 글을 쓸 수 있습니다.')).toBeVisible();
    await expect(page.locator('#post-save-form')).toHaveCount(0);

    // 발송된 메일에서 인증 링크를 꺼내 연다.
    // 메일은 DB 트랜잭션 밖에서 비동기로 나가므로(OutboxMailWorker) 곧바로 도착해 있지 않을 수
    // 있다. 도착할 때까지 짧게 기다린다 — 이것이 실제 사용자가 겪는 흐름이기도 하다.
    await expect
        .poll(async () => (await request.get(`/e2e/mails/latest?to=${encodeURIComponent(email)}`)).status(),
            { timeout: 10_000 })
        .toBe(200);

    const mail = await (await request.get(`/e2e/mails/latest?to=${encodeURIComponent(email)}`)).json();
    const verifyUrl = mail.text.match(/https?:\/\/\S+/)[0];
    await page.goto(verifyUrl);
    await expect(page.getByText('이메일 인증이 완료되었습니다')).toBeVisible();

    // 권한은 다시 로그인해야 반영된다(verify-result.html의 안내대로).
    await login(page, email);
    await page.goto('/posts/save');
    await expect(page.locator('#post-save-form')).toBeVisible();
});
