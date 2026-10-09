import { test, expect, PASSWORD, login, openAccountMenu, uniqueTitle } from './fixtures.js';

// 가입부터 시작하므로 로그인 상태 없이 돈다.
test.use({ storageState: { cookies: [], origins: [] } });

function newEmail() {
    return `${uniqueTitle('signup').toLowerCase()}@e2e.test`;
}

// 이 값은 작성자 이름으로 모두에게 공개된다는 점을 가입 전에 알린다.
test('이름 칸은 공개 닉네임으로 안내되고, 공개 범위 설명이 입력란에 연결돼 있다', async ({ page }) => {
    await page.goto('/signup');

    const name = page.getByLabel('공개 닉네임');
    await expect(name).toHaveAttribute('id', 'name');
    await expect(name).toHaveAccessibleDescription(/모든 방문자에게 표시됩니다/);
});

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

/**
 * 확인란을 고쳐 다시 일치시켜도 재제출 전까지 오류가 남아, 이미 해결된 문제처럼 보이지
 * 않았다.
 */
test('비밀번호 확인을 고쳐 다시 일치시키면 재제출 전에도 오류가 사라진다', async ({ page }) => {
    await page.goto('/signup');
    await page.locator('#name').fill('불일치해소');
    await page.locator('#email').fill(newEmail());
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(`${PASSWORD}xx`);
    await page.locator('#btn-signup').click();
    await expect(page.locator('#passwordConfirm-error')).toBeVisible();

    await page.locator('#passwordConfirm').fill(PASSWORD);

    await expect(page.locator('#passwordConfirm-error')).toBeEmpty();
    await expect(page.locator('#passwordConfirm')).not.toHaveAttribute('aria-invalid');
});

/**
 * 서버 DTO(SignUpRequestDto)가 72자를 상한으로 두는데, 입력에 maxlength가 없으면
 * 73자를 그대로 서버까지 보내 그때야 거절당했다. maxlength="72"가 실제로 입력 단계에서
 * 자르는지 확인한다.
 */
test('비밀번호는 72자를 넘겨 입력해도 72자로 잘린다', async ({ page }) => {
    await page.goto('/signup');
    await page.locator('#password').fill('a'.repeat(73));

    await expect(page.locator('#password')).toHaveValue('a'.repeat(72));
});

test('Enter로도 제출되고, 필수 입력이 비어 있으면 브라우저 검증이 먼저 막는다', async ({ page }) => {
    let requested = false;
    page.on('request', (request) => {
        if (request.method() === 'POST' && request.url().endsWith('/api/v1/users')) {
            requested = true;
        }
    });

    await page.goto('/signup');

    // 예전에는 "가입하기"가 type=button이라 Enter가 아무 일도 하지 않았고 required도 없었다.
    await page.locator('#name').press('Enter');
    await expect(page).toHaveURL(/\/signup$/);
    expect(requested, 'required가 제출 자체를 막는다').toBe(false);

    // 필수 입력을 채우면 Enter가 실제로 폼을 제출한다 — 확인란 불일치까지 도달하는 것으로 안다.
    await page.locator('#name').fill('엔터');
    await page.locator('#email').fill(newEmail());
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(`${PASSWORD}xx`);
    await page.locator('#passwordConfirm').press('Enter');

    await expect(page.locator('#passwordConfirm-error')).toBeVisible();
    expect(requested, '불일치는 서버까지 갈 필요가 없다').toBe(false);
});

/**
 * 계정 열거 방지. 이미 가입된 이메일로 가입을 시도해도 신규 가입과 똑같은 성공
 * 응답을 보여준다 — 응답이 갈리면 그 자체로 "이 주소가 가입되어 있다"를 확인하는 도구가
 * 된다. 대신 계정 주인에게만 보이는 안내 메일이 간다.
 */
test('이미 가입된 이메일로 가입해도 신규 가입과 같은 안내가 뜨고, 계정 주인에게만 메일이 간다', async ({ page, request }) => {
    await page.goto('/signup');
    await page.locator('#name').fill(uniqueTitle('중복'));
    await page.locator('#email').fill('user@e2e.test');
    await page.locator('#password').fill(PASSWORD);
    await page.locator('#passwordConfirm').fill(PASSWORD);
    await page.locator('#btn-signup').click();

    await page.waitForURL(/\/login/);
    await expect(page.locator('#flash')).toContainText('요청을 받았습니다');

    await expect
        .poll(async () => (await request.get(`/e2e/mails/latest?to=${encodeURIComponent('user@e2e.test')}`)).status(),
            { timeout: 10_000 })
        .toBe(200);
    const mail = await (await request.get(`/e2e/mails/latest?to=${encodeURIComponent('user@e2e.test')}`)).json();
    expect(mail.subject).toContain('이미 가입된 계정');
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
    await expect(page.locator('#flash')).toContainText('요청을 받았습니다');

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

    // GET은 확인 화면만 보여주고 토큰을 소비하지 않는다 — 메일
    // 보안 스캐너가 이 링크를 대신 열어도 인증이 끝나지 않는다. 사용자가 버튼을 눌러야
    // 실제로 소비된다.
    await page.goto(verifyUrl);
    await page.locator('#btn-verify-confirm').click();
    await expect(page.getByText('이메일 인증이 완료되었습니다')).toBeVisible();

    // 인증 성공이 지금 로그인된 세션의 권한도 즉시 갱신한다 —
    // 다시 로그인하지 않아도 곧바로 글을 쓸 수 있고, GUEST 전용 메뉴(인증 메일 재발송)도
    // 사라진다.
    await page.goto('/posts/save');
    await expect(page.locator('#post-save-form')).toBeVisible();
    await openAccountMenu(page);
    await expect(page.locator('#btn-resend-verification')).toHaveCount(0);
});
