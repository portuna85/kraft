import AxeBuilder from '@axe-core/playwright';
import { test, expect } from './fixtures.js';

/**
 * 홈(랜딩). e2e 프로파일은 당첨 이력이 비어 있으므로 "최신 회차" 섹션은 나오지 않는 상태가
 * 기준이다 — 그 경우에도 첫 화면이 성립하는지(빈 커뮤니티 문구 포함)를 확인한다.
 */

async function scanSevere(page) {
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const severe = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(severe, JSON.stringify(severe, null, 2)).toEqual([]);
}

test('첫 화면은 서비스 설명과 번호 추천 CTA를 먼저 보여준다', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('데이터로 살펴보는 로또 6/45');
    await expect(page.getByRole('link', { name: '번호 추천 받기' })).toHaveAttribute('href', '/recommend');
    await expect(page.getByRole('heading', { level: 2, name: '최근 커뮤니티' })).toBeVisible();
    // 게시판 화면의 제목이 첫 화면의 얼굴이 되지 않는다.
    await expect(page.getByRole('heading', { name: '전체 게시글' })).toHaveCount(0);
});

test('내비게이션의 커뮤니티 링크가 게시판으로 이어진다', async ({ page }) => {
    await page.goto('/');
    await page.locator('#site-nav').getByRole('link', { name: '커뮤니티' }).click();
    await expect(page).toHaveURL('/community');
    await expect(page.getByRole('heading', { level: 1, name: '전체 게시글' })).toBeVisible();
});

test('옛 게시판 주소(/?q=…)는 /community로 이동한다', async ({ page }) => {
    await page.goto('/?q=abc');
    await expect(page).toHaveURL('/community?q=abc');
});

test.describe('접근성·반응형', () => {
    test('라이트 모드 axe', async ({ page }) => {
        await page.emulateMedia({ colorScheme: 'light' });
        await page.goto('/');
        await scanSevere(page);
    });

    test('다크 모드 axe', async ({ page }) => {
        await page.emulateMedia({ colorScheme: 'dark' });
        await page.goto('/');
        await scanSevere(page);
    });

    test('360px에서 가로 넘침이 없다', async ({ page }) => {
        await page.setViewportSize({ width: 360, height: 740 });
        await page.goto('/');
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
        expect(overflow).toBeLessThanOrEqual(0);
    });
});
