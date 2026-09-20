import { test, expect, ACCOUNTS, PASSWORD, storageStateFor, login, openAccountMenu, uniqueTitle } from './fixtures.js';

test.describe('비밀번호 변경', () => {
    test.use({ storageState: storageStateFor('other') });

    test('현재 비밀번호가 틀리면 모달 안에서 알려준다', async ({ page }) => {
        await page.goto('/');
        await openAccountMenu(page);
        await page.getByRole('button', { name: '비밀번호 변경' }).click();

        const modal = page.locator('#changePasswordModal');
        await expect(modal).toBeVisible();

        await page.locator('#currentPassword').fill('WrongPass1!');
        await page.locator('#newPassword').fill('Another1!pass');
        await page.locator('#btn-change-password').click();

        // 화면 이동이 없으므로 flash가 아니라 모달 안에서 보여준다.
        await expect(page.locator('#change-password-error')).toBeVisible();
        await expect(modal).toBeVisible();
    });

    test('모달에서도 Enter로 제출되고, 빈 입력은 브라우저 검증이 먼저 막는다', async ({ page }) => {
        let requested = false;
        page.on('request', (request) => {
            if (request.method() === 'PUT' && request.url().endsWith('/api/v1/users/me/password')) {
                requested = true;
            }
        });

        await page.goto('/');
        await openAccountMenu(page);
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        await expect(page.locator('#changePasswordModal')).toBeVisible();

        // 예전에는 "변경하기"가 type=button이라 Enter가 아무 일도 하지 않았다.
        await page.locator('#currentPassword').press('Enter');
        expect(requested, 'required가 제출 자체를 막는다').toBe(false);

        await page.locator('#currentPassword').fill('WrongPass1!');
        await page.locator('#newPassword').fill('Another1!pass');
        await page.locator('#newPassword').press('Enter');

        await expect(page.locator('#change-password-error')).toBeVisible();
        expect(requested, 'Enter가 실제로 폼을 제출했다').toBe(true);
    });

    /**
     * F10: 요청이 진행 중일 때 모달을 닫고 다시 열면 폼이 reset된다. 그 늦은 응답(실패)이
     * 도착했을 때, 이미 새로 연(비어 있는) 폼 위에 낡은 오류를 덮어씌우면 안 된다 — 사용자는
     * 이 시도를 아직 한 번도 제출하지 않았다.
     */
    test('재열기 후에는 이전 시도의 늦은 실패 응답이 새 폼에 나타나지 않는다', async ({ page }) => {
        let releaseFirst;
        const gate = new Promise((resolve) => {
            releaseFirst = resolve;
        });
        await page.route('**/api/v1/users/me/password', async (route) => {
            await gate;
            await route.fulfill({ status: 400, contentType: 'application/json', body: '{"detail":"현재 비밀번호가 올바르지 않습니다."}' });
        });

        await page.goto('/');
        await openAccountMenu(page);
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        const modal = page.locator('#changePasswordModal');
        await expect(modal).toBeVisible();

        await page.locator('#currentPassword').fill('WrongPass1!');
        await page.locator('#newPassword').fill('Another1!pass');
        await page.locator('#btn-change-password').click();

        // 요청이 진행 중인 동안 모달을 닫는다(응답은 아직 gate에 잡혀 있다).
        await modal.getByRole('button', { name: '취소' }).click();
        await expect(modal).toBeHidden();

        // 다시 연다 — 폼이 reset되어 있어야 하고, 아직 실패 안내가 없어야 한다.
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        await expect(modal).toBeVisible();
        await expect(page.locator('#currentPassword')).toHaveValue('');
        await expect(page.locator('#change-password-error')).toBeHidden();

        // 이제야 이전 시도의 실패 응답이 도착한다 — 지금 보이는(비어 있는) 폼에 나타나면 안 된다.
        releaseFirst();
        await page.waitForTimeout(300);
        await expect(page.locator('#change-password-error')).toBeHidden();
        await expect(modal).toBeVisible();
    });
});

test.describe('비밀번호 변경 성공', () => {
    // 이 스펙은 비밀번호를 실제로 바꾸므로 다른 계정과 섞이면 안 된다.
    // storageState 없이 새로 로그인해 쓰고, 끝에서 원래대로 되돌린다.
    test.use({ storageState: { cookies: [], origins: [] } });

    test('변경하면 모든 세션이 끊기고 로그인 화면으로 간다', async ({ page }) => {
        const newPassword = `New${uniqueTitle('p').slice(-6)}!aA1`;

        await login(page, ACCOUNTS.admin.email);
        await openAccountMenu(page);
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        await page.locator('#currentPassword').fill(PASSWORD);
        await page.locator('#newPassword').fill(newPassword);
        await page.locator('#btn-change-password').click();

        await page.waitForURL(/\/login/);
        await expect(page.locator('#flash')).toContainText('비밀번호가 변경되었습니다');

        // 새 비밀번호로만 들어갈 수 있다.
        await login(page, ACCOUNTS.admin.email, newPassword);
        await openAccountMenu(page);
        await expect(page.locator('.kraft-actions__name')).toContainText(ACCOUNTS.admin.name);

        // 다음 실행을 위해 되돌린다.
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        await page.locator('#currentPassword').fill(newPassword);
        await page.locator('#newPassword').fill(PASSWORD);
        await page.locator('#btn-change-password').click();
        await page.waitForURL(/\/login/);
    });
});

test.describe('비밀번호 앞뒤 공백', () => {
    // 가입부터 시작하므로 로그인 상태 없이 돈다.
    test.use({ storageState: { cookies: [], origins: [] } });

    /**
     * 예전에는 회원가입·재설정은 비밀번호 원문을 그대로 보내는데, 비밀번호 변경·탈퇴 모달만
     * dom.js의 trim하는 valueOf()로 값을 읽었다. 그래서 앞뒤 공백을 포함해 가입한 비밀번호를
     * "현재 비밀번호"로 그대로 입력해도 trim된 값과 비교되어 거절됐다(개선 보고서 "비밀번호
     * 공백 처리 불일치와 길이 정책"). 이제는 어디서도 trim하지 않아야 한다.
     */
    test('공백을 포함해 가입한 비밀번호를 그대로 입력해도 비밀번호 변경이 통과한다', async ({ page }) => {
        const email = `${uniqueTitle('space').toLowerCase()}@e2e.test`;
        const paddedPassword = `  ${PASSWORD}  `;

        await page.goto('/signup');
        await page.locator('#name').fill('공백테스트');
        await page.locator('#email').fill(email);
        await page.locator('#password').fill(paddedPassword);
        await page.locator('#passwordConfirm').fill(paddedPassword);
        await page.locator('#btn-signup').click();
        await page.waitForURL(/\/login/);

        await login(page, email, paddedPassword);

        const newPassword = `New${uniqueTitle('p').slice(-6)}!aA1`;
        await openAccountMenu(page);
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        await page.locator('#currentPassword').fill(paddedPassword);
        await page.locator('#newPassword').fill(newPassword);
        await page.locator('#btn-change-password').click();

        await page.waitForURL(/\/login/);
        await expect(page.locator('#flash')).toContainText('비밀번호가 변경되었습니다');
        await login(page, email, newPassword);
        await openAccountMenu(page);
        await expect(page.locator('.kraft-actions__name')).toContainText('공백테스트');
    });
});

test.describe('인증 메일 재발송', () => {
    test.use({ storageState: storageStateFor('guest') });

    test('미인증 계정은 재발송을 요청할 수 있다', async ({ page }) => {
        await page.goto('/');
        await openAccountMenu(page);
        await page.locator('#btn-resend-verification').click();

        const modal = page.locator('#resendVerificationModal');
        await expect(modal).toBeVisible();

        await page.locator('#btn-confirm-resend').click();

        await expect(page.locator('#app-toast')).toContainText('인증 메일을 다시 보냈습니다');
        await expect(modal).toBeHidden();
    });
});
