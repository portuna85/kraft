import { test as base, expect } from '@playwright/test';

export const ACCOUNTS = {
    admin: { email: 'admin@e2e.test', name: '관리자' },
    user: { email: 'user@e2e.test', name: '테스터' },
    other: { email: 'other@e2e.test', name: '다른사람' },
    guest: { email: 'guest@e2e.test', name: '미인증' },
};

/** E2eDataInitializer가 모든 시드 계정에 쓰는 공용 비밀번호. */
export const PASSWORD = 'E2e!pass1';

export const storageStateFor = (role) => `e2e/.auth/${role}.json`;

/**
 * 모든 스펙이 공유하는 test. 페이지에서 처리되지 않은 JS 오류가 하나라도 나면 실패시킨다.
 *
 * 이 장치가 중요한 이유: jQuery는 선택자가 비면 조용히 아무 일도 하지 않지만, 네이티브 DOM은
 * null에 접근하는 순간 던진다. layout/footer 프래그먼트는 모든 페이지에 들어가므로 기능
 * 초기화 코드가 전 페이지에서 돌고, 한 곳에서 던지면 그 뒤에 등록될 기능이 전부 죽는다.
 * jQuery를 걷어내는 과정에서 가장 흔하게 생길 회귀가 바로 이것이라, 모든 스펙을 그 회귀의
 * 탐지기로 만들어 둔다.
 */
export const test = base.extend({
    page: async ({ page }, use) => {
        const errors = [];
        page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
        page.on('console', (message) => {
            if (message.type() !== 'error') {
                return;
            }
            // 브라우저가 4xx·5xx 응답 자체에 대해 남기는 로그는 JS 오류가 아니다. 오류 응답을
            // 일부러 만들어 안내 문구를 확인하는 스펙이 있으므로 여기서 걸러낸다.
            const text = message.text();
            if (text.includes('Failed to load resource')) {
                return;
            }
            errors.push(`console.error: ${text}`);
        });

        await use(page);

        expect(errors, '페이지에서 처리되지 않은 JS 오류').toEqual([]);
    },
});

export { expect };

/** 시드 데이터와 섞이지 않도록 이 실행에서만 쓰는 제목을 만든다. */
export function uniqueTitle(prefix) {
    return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

/**
 * 제목으로 글을 찾아 상세 화면을 연다.
 *
 * 목록 첫 페이지에서 찾으면 안 된다 — 한 개의 인메모리 DB를 모든 스펙이 공유하므로 글이
 * 쌓일수록 원하는 글이 페이지 밖으로 밀려난다. 검색으로 좁혀야 실행 순서와 무관해진다.
 */
export async function openPostByTitle(page, title) {
    await page.goto(`/?q=${encodeURIComponent(title)}`);
    // 목록의 링크는 분류 배지와 제목을 함께 담고 있어(접근성 이름이 "자유 제목" 꼴) 제목만으로
    // 정확히 일치시킬 수 없다. 링크 안에 제목이 들어 있는지로 찾는다.
    await page.locator('.post-list__title').filter({ hasText: title }).first().click();
    await page.waitForURL(/\/posts\/update\/\d+$/);
}

/** 폼 로그인. storageState를 만들 때와, 세션을 새로 얻어야 할 때 쓴다. */
export async function login(page, email, password = PASSWORD) {
    await page.goto('/login');
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(password);
    await page.getByRole('button', { name: '로그인' }).click();
    await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

/**
 * 계정 관련 버튼·링크(인증 메일 재발송·비밀번호 변경·신고 처리·로그아웃·회원 탈퇴)는
 * 이제 닉네임 펼침 메뉴(#account-menu) 안에 있다(layout/navbar.html, site-nav.js).
 * 그 안의 버튼·링크를 누르기 전에 이 함수를 불러야 한다.
 *
 * 768px 미만에서는 그 전에 바깥 "메뉴" 토글(#btn-nav-toggle)부터 열어야 계정 토글
 * 자체가 보인다 — 768px 이상에서는 바깥 토글이 숨어 있으므로 `isVisible()`이 false라
 * 건너뛴다(그 화면에서는 바깥 메뉴가 항상 펼쳐져 있다).
 */
export async function openAccountMenu(page) {
    const navToggle = page.locator('#btn-nav-toggle');
    if (await navToggle.isVisible()) {
        // 이미 열려 있으면 다시 누르지 않는다 — site-nav.js의 토글은 현재 상태를 뒤집으므로,
        // 열린 채로 한 번 더 부르면 오히려 닫혀 버린다.
        if (await page.locator('#site-nav').isHidden()) {
            await navToggle.click();
        }
    }

    const accountToggle = page.locator('#btn-account-toggle');
    if (await accountToggle.isVisible() && await page.locator('#account-menu').isHidden()) {
        await accountToggle.click();
    }
}
