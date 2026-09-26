import { byId } from '../core/dom.js';

/**
 * 다크 모드 토글(11단계). 시스템 → 밝게 → 어둡게를 순환하는 3단계 버튼이다.
 *
 * theme-init.js(별도 파일, defer 없이 head에서 동기 실행)가 첫 페인트 전에 같은 저장
 * 키(kraft:theme)로 <html>의 data-theme·data-bs-theme을 이미 정해 둔 상태에서 시작한다 —
 * 이 모듈은 그 초기 상태에 맞춰 버튼 문구를 맞추고, 이후 클릭과 시스템 설정 변경에
 * 반응한다. 저장 키·읽기 로직을 바꾸면 theme-init.js도 함께 고쳐야 한다.
 */
const STORAGE_KEY = 'kraft:theme';
const ORDER = ['system', 'light', 'dark'];
const LABELS = { system: '테마: 시스템', light: '테마: 밝게', dark: '테마: 어둡게' };

export function init() {
    const button = /** @type {HTMLButtonElement | null} */ (byId('btn-theme-toggle'));
    if (!button) {
        return;
    }

    let current = readStored();
    render(button, current);

    // 'system'일 때만 의미가 있다 — Kraft 자체 토큰은 CSS 미디어 쿼리가 알아서 따라가지만
    // (_tokens.scss), Bootstrap은 속성 기반(data-bs-theme)이라 시스템 설정이 바뀌어도 JS가
    // 갱신해 주지 않으면 옛 값에 머문다.
    const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    media?.addEventListener('change', () => {
        if (current === 'system') {
            applyBootstrapAttr('system');
        }
    });

    button.addEventListener('click', () => {
        current = ORDER[(ORDER.indexOf(current) + 1) % ORDER.length];
        persist(current);
        apply(current);
        render(button, current);
    });
}

function readStored() {
    try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        return ORDER.includes(raw) ? raw : 'system';
    } catch {
        return 'system';
    }
}

function persist(value) {
    try {
        window.localStorage.setItem(STORAGE_KEY, value);
    } catch {
        // 저장 실패는 이번 열람에서만 적용되는 정도로 넘어간다.
    }
}

function apply(value) {
    const root = document.documentElement;
    if (value === 'system') {
        root.removeAttribute('data-theme');
    } else {
        root.setAttribute('data-theme', value);
    }
    applyBootstrapAttr(value);
}

function applyBootstrapAttr(value) {
    const root = document.documentElement;
    if (value === 'system') {
        const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        root.setAttribute('data-bs-theme', prefersDark ? 'dark' : 'light');
    } else {
        root.setAttribute('data-bs-theme', value);
    }
}

function render(button, value) {
    button.textContent = LABELS[value];
}
