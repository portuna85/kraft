import { test, expect, storageStateFor } from './fixtures.js';

/**
 * 시각 회귀 기준선.
 *
 * 동작은 나머지 스펙들이 덮지만 **생김새는 아무도 보지 않는다.** Bootstrap CSS를 필요한
 * 컴포넌트만 담은 커스텀 빌드로 바꾸는 작업은 정확히 그 사각지대를 건드리므로, 바꾸기 전의
 * 화면을 찍어 두고 바꾼 뒤와 비교한다.
 *
 * 날짜·글 번호처럼 실행마다 달라지는 부분은 mask로 가린다 — 그러지 않으면 매번 다르다고 나온다.
 */

// 렌더링 차이를 조금 허용한다. 폰트 힌팅 등으로 몇 픽셀은 늘 흔들린다.
const PIXEL_TOLERANCE = { maxDiffPixelRatio: 0.01 };

test.describe('로그인 전 화면', () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test('로그인 화면', async ({ page }) => {
        await page.goto('/login');
        await expect(page).toHaveScreenshot('login.png', PIXEL_TOLERANCE);
    });

    test('회원가입 화면', async ({ page }) => {
        await page.goto('/signup');
        await expect(page).toHaveScreenshot('signup.png', PIXEL_TOLERANCE);
    });
});

test.describe('로그인 후 화면', () => {
    test.use({ storageState: storageStateFor('user') });

    test('글쓰기 화면 - 폼 컨트롤과 버튼', async ({ page }) => {
        await page.goto('/posts/save');
        await expect(page).toHaveScreenshot('post-save.png', PIXEL_TOLERANCE);
    });

    test('목록 한 줄의 생김새', async ({ page }) => {
        // 목록 전체를 찍으면 다른 스펙이 만든 글이 섞여 매번 달라진다. 시드 글 하나만
        // 나오도록 검색으로 좁혀 실행 순서와 무관하게 만든다.
        await page.goto('/?q=%EB%8B%A4%EB%A5%B8%20%EC%82%AC%EB%9E%8C%EC%9D%98%20%EA%B8%80');
        const row = page.locator('.post-list__item').first();
        await expect(row).toBeVisible();
        await expect(row).toHaveScreenshot('post-list-row.png', {
            ...PIXEL_TOLERANCE,
            // 글 번호·최종수정일·조회수는 실행마다 다르다.
            mask: [
                row.locator('.post-list__no'),
                row.locator('.post-list__date'),
                row.locator('.post-list__views'),
            ],
        });
    });

    test('비밀번호 변경 모달', async ({ page }) => {
        await page.goto('/');
        await page.getByRole('button', { name: '비밀번호 변경' }).click();
        const modal = page.locator('#changePasswordModal');
        await expect(modal).toBeVisible();
        // 페이드 인이 끝난 뒤에 찍어야 흔들리지 않는다.
        await page.waitForTimeout(400);
        // 페이지 전체가 아니라 모달만 찍는다 — 뒤 배경의 목록은 실행마다 다르다.
        await expect(modal.locator('.modal-dialog')).toHaveScreenshot(
            'modal-change-password.png',
            PIXEL_TOLERANCE,
        );
    });

    test('토스트 알림', async ({ page }) => {
        await page.goto('/');
        // 실제 동작으로 띄우면 화면이 바뀌므로, 토스트 컴포넌트만 직접 보여 준다.
        await page.evaluate(() => {
            const el = document.getElementById('app-toast');
            el.classList.add('bg-success', 'text-white');
            document.getElementById('app-toast-title').textContent = '완료';
            document.getElementById('app-toast-body').textContent = '토스트 모양 확인용입니다.';
            bootstrap.Toast.getOrCreateInstance(el, { autohide: false }).show();
        });
        await page.waitForTimeout(400);
        await expect(page.locator('#toast-container')).toHaveScreenshot(
            'toast.png',
            PIXEL_TOLERANCE,
        );
    });

    test('로그인 실패 알림(alert)', async ({ page }) => {
        await page.goto('/login?error');
        await expect(page.locator('.alert')).toHaveScreenshot('alert-danger.png', PIXEL_TOLERANCE);
    });
});
