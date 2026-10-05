import { byId } from '../core/dom.js';
import { readItem, writeItem } from '../core/storage.js';

/**
 * 다크 모드 토글(11단계). 시스템 → 밝게 → 어둡게를 순환하는 3단계 버튼이다.
 *
 * theme-init.js(별도 파일, defer 없이 head에서 동기 실행)가 첫 페인트 전에 같은 저장
 * 키(kraft:theme)로 <html>의 data-theme·data-bs-theme을 이미 정해 둔 상태에서 시작한다 —
 * 이 모듈은 그 초기 상태에 맞춰 버튼 문구를 맞추고, 이후 클릭과 시스템 설정 변경에
 * 반응한다. 저장 키·순서·적용 로직은 theme-init.js가 window.kraftTheme으로 내놓은 것을 그대로 쓴다(FE-31).
 */
const LABELS = { system: '테마: 시스템', light: '테마: 밝게', dark: '테마: 어둡게' };

export function init() {
    // theme-init.js(head, 동기)가 먼저 실행되어 window.kraftTheme을 만든다. 없으면 토글을 붙이지 않는다.
    const theme = window.kraftTheme;
    if (!theme) {
        return;
    }
    const { ORDER } = theme;
    const button = /** @type {HTMLButtonElement | null} */ (byId('btn-theme-toggle'));
    if (!button) {
        return;
    }

    let current = readStored(theme);
    render(button, current);

    // 'system'일 때만 의미가 있다 — Kraft 자체 토큰은 CSS 미디어 쿼리가 알아서 따라가지만
    // (_tokens.scss), Bootstrap은 속성 기반(data-bs-theme)이라 시스템 설정이 바뀌어도 JS가
    // 갱신해 주지 않으면 옛 값에 머문다.
    const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    media?.addEventListener('change', () => {
        if (current === 'system') {
            theme.apply('system');
        }
    });

    button.addEventListener('click', () => {
        current = ORDER[(ORDER.indexOf(current) + 1) % ORDER.length];
        persist(theme, current);
        theme.apply(current);
        render(button, current);
    });
}

function readStored(theme) {
    const raw = readItem(theme.STORAGE_KEY);
    return theme.ORDER.includes(raw) ? raw : 'system';
}

function persist(theme, value) {
    // 저장 실패는 이번 열람에서만 적용되는 정도로 넘어간다.
    writeItem(theme.STORAGE_KEY, value);
}

function render(button, value) {
    button.textContent = LABELS[value];
}
