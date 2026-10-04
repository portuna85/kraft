import { test, expect, storageStateFor } from './fixtures.js';

/**
 * 관리자 "추천 이력 수집" 화면. 실제 수집(동행복권 호출)은 E2E에서 돌리지 않는다 — 외부 사이트에
 * 의존하고 운영 데이터를 바꾸는 동작이라, 버튼의 확인 단계(취소하면 아무 일도 없음)까지만 본다.
 */
test.describe('관리자', () => {
    test.use({ storageState: storageStateFor('admin') });

    test('수집 상태와 다음 예약 시각, 이력 영역, 지금 수집 버튼이 보인다', async ({ page }) => {
        await page.goto('/admin/recommendations');

        await expect(page.getByRole('heading', { name: '추천 이력 수집', level: 1 })).toBeVisible();
        await expect(page.getByText('다음 예약 수집')).toBeVisible();
        await expect(page.getByText('검증 완료 회차')).toBeVisible();
        await expect(page.getByRole('heading', { name: '최근 수집 이력' })).toBeVisible();
        await expect(page.locator('#btn-fetch-now')).toBeEnabled();
    });

    test('지금 수집은 확인을 받고, 취소하면 아무 요청도 보내지 않는다', async ({ page }) => {
        const requests = [];
        page.on('request', (request) => {
            if (request.url().includes('/api/v1/admin/recommendations/fetch')) {
                requests.push(request.url());
            }
        });
        await page.goto('/admin/recommendations');

        await page.locator('#btn-fetch-now').click();
        const dialog = page.locator('#confirmDeleteModal');
        await expect(dialog).toBeVisible();
        await expect(dialog).toContainText('지금 수집');
        await dialog.getByRole('button', { name: '취소' }).click();
        await expect(dialog).toBeHidden();

        expect(requests).toHaveLength(0);
        await expect(page.locator('#btn-fetch-now')).toBeEnabled();
    });
});

test.describe('일반 사용자', () => {
    test.use({ storageState: storageStateFor('user') });

    test('관리자가 아니면 수집 화면에 들어갈 수 없다', async ({ page }) => {
        const response = await page.goto('/admin/recommendations');

        expect(response.status()).toBe(403);
    });
});
