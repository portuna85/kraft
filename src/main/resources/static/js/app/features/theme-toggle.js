import { byId } from '../core/dom.js';
import { readItem, writeItem } from '../core/storage.js';

/**
 * 다크 모드 토글. 시스템 → 밝게 → 어둡게를 순환하는 3단계 버튼이다. 화면에는 현재 상태의 아이콘(CSS가 data-state로 고른다)만 보이고, 접근 가능한 이름은 버튼 안의 .visually-hidden 글자다.
 *
 * theme-init.js(head에서 동기 실행)가 첫 페인트 전에 같은 저장 키(kraft:theme)로 <html>의 data-theme·data-bs-theme을 이미 정해 둔다. 이 모듈은 그 초기 상태에 맞춰 버튼 문구를 맞추고
 * 이후 클릭과 시스템 설정 변경에 반응하며, 저장 키·순서·적용 로직은 theme-init.js가 window.kraftTheme으로 내놓은 것을 그대로 쓴다.
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

    // 'system'일 때만 의미가 있다 — Kraft 자체 토큰은 CSS 미디어 쿼리가 따라가지만(_tokens.scss), Bootstrap은 속성 기반(data-bs-theme)이라 JS가 갱신해야 시스템 설정 변경을 따라간다.
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
    return raw !== null && theme.ORDER.includes(raw) ? raw : 'system';
}

function persist(theme, value) {
    // 저장 실패는 이번 열람에서만 적용되는 정도로 넘어간다.
    writeItem(theme.STORAGE_KEY, value);
}

function render(button, value) {
    button.dataset.state = value;
    // 아이콘 svg를 지우지 않도록 보이지 않는 이름 글자만 바꾼다. 그 요소가 없는 마크업이면 버튼 글자 자체를 바꾼다.
    const label = button.querySelector('.visually-hidden');
    (label ?? button).textContent = LABELS[value];
}
