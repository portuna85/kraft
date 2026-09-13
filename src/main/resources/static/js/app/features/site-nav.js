import { byId, on } from '../core/dom.js';

/**
 * 헤더 오른쪽 버튼 모음의 토글. 768px 미만에서는 hidden 속성으로 열림·닫힘을 표현하고,
 * 그 이상에서는 CSS가 항상 펼쳐 보이므로 이 스크립트가 상태를 건드리지 않는다.
 */
export function init() {
    const toggle = byId('btn-nav-toggle');
    const nav = byId('site-nav');
    if (!toggle || !nav) {
        return;
    }

    const isOpen = () => toggle.getAttribute('aria-expanded') === 'true';

    const setOpen = (open) => {
        nav.hidden = !open;
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    };

    on(toggle, 'click', () => setOpen(!isOpen()));

    on(document, 'keydown', (event) => {
        if (event.key === 'Escape' && isOpen()) {
            setOpen(false);
            // 닫은 뒤 포커스를 토글로 되돌려야 키보드 사용자가 맥락을 잃지 않는다.
            toggle.focus();
        }
    });
}
