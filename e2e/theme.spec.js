import { test, expect } from './fixtures.js';

/**
 * #btn-theme-toggle은 #site-nav 안에 있다 — 768px 미만(mobile-webkit 포함)에서는 바깥
 * "메뉴" 토글(#btn-nav-toggle)을 먼저 열어야 보인다(openAccountMenu와 같은 이유,
 * fixtures.js 참고). 계정 메뉴가 아니라 테마 토글만 필요하므로 그 전반부만 가져온다.
 */
async function ensureThemeToggleVisible(page) {
    const navToggle = page.locator('#btn-nav-toggle');
    if (await navToggle.isVisible() && await page.locator('#site-nav').isHidden()) {
        await navToggle.click();
    }
}

/**
 * 다크 모드(11단계). theme-init.js(첫 페인트 전 동기 실행)와 theme-toggle.js(계정 메뉴
 * 옆 토글 버튼, 시스템→밝게→어둡게 순환)를 함께 확인한다.
 *
 * Kraft 자체 토큰(--kraft-*)은 CSS의 prefers-color-scheme 미디어 쿼리로 시스템 설정을
 * 자동으로 따르므로 "시스템" 상태에서는 <html>에 data-theme을 붙이지 않는다. Bootstrap은
 * 속성 기반(data-bs-theme)이라 "시스템" 상태에서도 JS가 지금 시점의 설정을 읽어 붙여 둔다
 * (theme-init.js) — 그래서 이 값은 "시스템"일 때도 항상 light/dark 중 하나로 채워져 있다.
 */

test('시스템이 다크면 data-bs-theme이 dark로 채워진다(시작 상태는 손대지 않아도)', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');

    await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'dark');
    // 시스템을 따르는 동안에는 Kraft 쪽 명시적 선택이 없다 — CSS 미디어 쿼리에 맡긴다.
    await expect(page.locator('html')).not.toHaveAttribute('data-theme', /.+/);
});

test('토글을 누르면 시스템→밝게→어둡게 순으로 바뀌고 새로고침해도 유지된다', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');

    const toggle = page.locator('#btn-theme-toggle');
    await ensureThemeToggleVisible(page);
    await expect(toggle).toHaveText('테마: 시스템');

    await toggle.click();
    await expect(toggle).toHaveText('테마: 밝게');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'light');

    // 새로고침해도(theme-init.js가 저장된 값을 다시 읽어) 같은 상태로 시작한다.
    // 새로고침하면 모바일 메뉴도 접힌 상태로 되돌아간다 — 다시 열어야 한다.
    await page.reload();
    await ensureThemeToggleVisible(page);
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    await expect(toggle).toHaveText('테마: 밝게');

    await toggle.click();
    await expect(toggle).toHaveText('테마: 어둡게');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    await toggle.click();
    await expect(toggle).toHaveText('테마: 시스템');
    await expect(page.locator('html')).not.toHaveAttribute('data-theme', /.+/);
});

test('"밝게"를 고르면 시스템이 다크여도 밝게 유지된다', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await ensureThemeToggleVisible(page);

    await page.locator('#btn-theme-toggle').click(); // 시스템 → 밝게
    await expect(page.locator('#btn-theme-toggle')).toHaveText('테마: 밝게');

    const bg = await page.locator('body').evaluate((el) => getComputedStyle(el).backgroundColor);
    // 라이트 --kraft-bg(#f8fafc)는 밝은 색이다 — 어두운 배경(rgb 값이 전반적으로 낮음)이면
    // 실패한다. 정확한 rgb 문자열 비교 대신 밝기만 확인해 토큰 값이 바뀌어도 깨지지 않게 한다.
    const [r, g, b] = bg.match(/\d+/g).map(Number);
    expect((r + g + b) / 3, `밝은 배경이어야 하는데 ${bg}`).toBeGreaterThan(200);
});
