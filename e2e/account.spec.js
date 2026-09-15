import { test, expect, ACCOUNTS, PASSWORD, storageStateFor, login, uniqueTitle } from './fixtures.js';

test.describe('비밀번호 변경', () => {
    test.use({ storageState: storageStateFor('other') });

    test('현재 비밀번호가 틀리면 모달 안에서 알려준다', async ({ page }) => {
        await page.goto('/');
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
});

test.describe('비밀번호 변경 성공', () => {
    // 이 스펙은 비밀번호를 실제로 바꾸므로 다른 계정과 섞이면 안 된다.
    // storageState 없이 새로 로그인해 쓰고, 끝에서 원래대로 되돌린다.
    test.use({ storageState: { cookies: [], origins: [] } });

    test('변경하면 모든 세션이 끊기고 로그인 화면으로 간다', async ({ page }) => {
        const newPassword = `New${uniqueTitle('p').slice(-6)}!aA1`;

        await login(page, ACCOUNTS.admin.email);
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        await page.locator('#currentPassword').fill(PASSWORD);
        await page.locator('#newPassword').fill(newPassword);
        await page.locator('#btn-change-password').click();

        await page.waitForURL(/\/login/);
        await expect(page.locator('#flash')).toContainText('비밀번호가 변경되었습니다');

        // 새 비밀번호로만 들어갈 수 있다.
        await login(page, ACCOUNTS.admin.email, newPassword);
        await expect(page.locator('.kraft-actions__name')).toContainText(ACCOUNTS.admin.name);

        // 다음 실행을 위해 되돌린다.
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        await page.locator('#currentPassword').fill(newPassword);
        await page.locator('#newPassword').fill(PASSWORD);
        await page.locator('#btn-change-password').click();
        await page.waitForURL(/\/login/);
    });
});

test.describe('인증 메일 재발송', () => {
    test.use({ storageState: storageStateFor('guest') });

    test('미인증 계정은 재발송을 요청할 수 있다', async ({ page }) => {
        await page.goto('/');
        await page.locator('#btn-resend-verification').click();

        const modal = page.locator('#resendVerificationModal');
        await expect(modal).toBeVisible();

        await page.locator('#btn-confirm-resend').click();

        await expect(page.locator('#app-toast')).toContainText('인증 메일을 다시 보냈습니다');
        await expect(modal).toBeHidden();
    });
});
