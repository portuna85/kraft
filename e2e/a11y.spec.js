import AxeBuilder from '@axe-core/playwright';
import { test, expect, storageStateFor, openPostByTitle } from './fixtures.js';

/**
 * 접근성 자동 검사(A-FE-13). 기능·시각 회귀만 보던 e2e에 axe-core 스캔을 더한다.
 *
 * `serious`·`critical` 위반만 실패시킨다(`withTags`) — `minor`·`moderate`까지 막으면
 * 관련 없는 색상·문구 변경이 접근성과 무관한 이유로 CI를 막을 수 있어, 급한 것만 걸러 사람이
 * 보게 한다(다른 검사들의 audit:check와 같은 판단 기준).
 *
 * 각 화면은 이미 기능 스펙에서 로그인 상태를 만들어 두었다 — 여기서는 그 storageState를
 * 재사용해 별도로 로그인 흐름을 반복하지 않는다.
 */

async function scan(page) {
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const severe = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(severe, JSON.stringify(severe, null, 2)).toEqual([]);
}

test.describe('로그인 없이 보는 화면', () => {
    test('목록', async ({ page }) => {
        await page.goto('/');
        await scan(page);
    });

    test('로그인', async ({ page }) => {
        await page.goto('/login');
        await scan(page);
    });

    test('가입', async ({ page }) => {
        await page.goto('/signup');
        await scan(page);
    });
});

test.describe('로그인한 화면', () => {
    test.use({ storageState: storageStateFor('user') });

    test('상세', async ({ page }) => {
        await openPostByTitle(page, '테스터의 글');
        await scan(page);
    });

    test('글쓰기', async ({ page }) => {
        await page.goto('/posts/save');
        await scan(page);
    });

    test('번호 추천', async ({ page }) => {
        await page.goto('/recommend');
        await scan(page);
    });
});

test.describe('관리자 화면', () => {
    test.use({ storageState: storageStateFor('admin') });

    test('신고 목록', async ({ page }) => {
        await page.goto('/admin/reports');
        await scan(page);
    });
});

/**
 * 11단계에서 손으로 계산한 다크 모드 대비 값이 유지되는지를 이 스캔이 자동으로 지킨다.
 * 목록 하나만 대표로 본다 — 색 토큰(--kraft-*)이 전역 CSS 변수라 화면마다 다시 볼 이유가
 * 없다(theme.spec.js와 같은 판단).
 */
test('다크 모드에서도 심각한 위반이 없다', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await scan(page);
});
