import { test, expect, ACCOUNTS, storageStateFor } from './fixtures.js';

test.describe('모바일 헤더 메뉴', () => {
    test.use({ storageState: storageStateFor('user'), viewport: { width: 390, height: 844 } });

    test('토글로 열고 닫으며, Escape로도 닫히고 포커스가 돌아온다', async ({ page }) => {
        await page.goto('/');

        const toggle = page.locator('#btn-nav-toggle');
        const nav = page.locator('#site-nav');

        await expect(toggle).toBeVisible();
        await expect(nav).toBeHidden();
        await expect(toggle).toHaveAttribute('aria-expanded', 'false');

        await toggle.click();
        await expect(nav).toBeVisible();
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');

        await page.keyboard.press('Escape');
        await expect(nav).toBeHidden();
        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
        await expect(toggle).toBeFocused();
    });
});

test.describe('넓은 화면', () => {
    test.use({ storageState: storageStateFor('user'), viewport: { width: 1280, height: 800 } });

    test('토글 없이 계정 메뉴가 그대로 보인다', async ({ page }) => {
        await page.goto('/');

        await expect(page.locator('#btn-nav-toggle')).toBeHidden();
        await expect(page.locator('.kraft-actions__name')).toContainText(ACCOUNTS.user.name);
        await expect(page.locator('#btn-logout')).toBeVisible();
    });
});

test.describe('로그인 복귀', () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test('검색 중이던 화면에서 로그인하면 검색어까지 유지된다', async ({ page }) => {
        await page.goto('/?q=%EA%B3%B5%EC%A7%80');

        await page.getByRole('link', { name: '로그인' }).click();
        await page.locator('#username').fill('user@e2e.test');
        await page.locator('#password').fill('E2e!pass1');
        await page.getByRole('button', { name: '로그인' }).click();

        // 쿼리 문자열이 보존되지 않으면 목록 첫 화면으로 떨어진다.
        await expect(page).toHaveURL(/\?q=/);
    });
});

test.describe('미인증 안내', () => {
    test.use({ storageState: storageStateFor('guest') });

    test('GUEST에게는 글쓰기 폼 대신 인증 안내가 보인다', async ({ page }) => {
        await page.goto('/posts/save');

        await expect(page.getByText('이메일 인증을 완료해야 글을 쓸 수 있습니다.')).toBeVisible();
        await expect(page.locator('#post-save-form')).toHaveCount(0);
    });

    test('GUEST에게는 댓글 입력창 대신 인증 안내가 보인다', async ({ page }) => {
        // 시드 게시글이면 무엇이든 된다 — 댓글 영역의 안내만 본다.
        await page.goto('/');
        await page.locator('.post-list__title').first().click();

        // 글쓰기와 댓글이 같은 규칙(WriteAccessPolicy)을 쓰므로 안내 문장도 하나다.
        await expect(page.locator('.comments__login-hint')).toContainText('이메일 인증을 완료해야');
        await expect(page.locator('#comment-content')).toHaveCount(0);
    });
});
