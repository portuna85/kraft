import { mkdirSync } from 'node:fs';
import { test as setup, expect } from '@playwright/test';
import { ACCOUNTS, login, storageStateFor } from './fixtures.js';

/**
 * 시드 계정별로 한 번씩 실제 폼 로그인을 거쳐 세션 쿠키를 파일로 남긴다. 각 스펙은
 * test.use({ storageState: ... })로 그 상태에서 시작하므로 매번 로그인을 반복하지 않는다.
 *
 * CSRF 토큰은 세션마다 다르지만 페이지를 열 때 메타 태그에서 새로 읽으므로 저장할 필요가 없다.
 */
for (const [role, account] of Object.entries(ACCOUNTS)) {
    setup(`${role} 로그인 상태 저장`, async ({ page }) => {
        mkdirSync('e2e/.auth', { recursive: true });

        await login(page, account.email);

        // 헤더에 닉네임이 보이면 인증된 세션이다.
        await expect(page.locator('.kraft-actions__name')).toContainText(account.name);

        await page.context().storageState({ path: storageStateFor(role) });
    });
}
