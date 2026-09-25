import { byId, on } from '../core/dom.js';

/**
 * aria-expanded + hidden 속성으로 열림·닫힘을 표현하는 펼침 버튼 하나를 초기화한다.
 * 닫힐 때 실행할 부수 효과(onClose)를 받아, 바깥 메뉴가 닫힐 때 그 안의 계정 메뉴도
 * 함께 닫는 식의 연쇄 동작에 재사용한다.
 */
function createDisclosure(toggle, panel, { onClose } = {}) {
    const isOpen = () => toggle.getAttribute('aria-expanded') === 'true';

    const setOpen = (open) => {
        panel.hidden = !open;
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        if (!open) {
            onClose?.();
        }
    };

    on(toggle, 'click', () => setOpen(!isOpen()));

    return { isOpen, setOpen };
}

/**
 * 헤더 오른쪽 버튼 모음(바깥 모바일 메뉴)과 그 안의 닉네임 계정 펼침 메뉴를 초기화한다.
 * 바깥은 768px 미만에서는 hidden 속성으로 열림·닫힘을 표현하고, 그 이상에서는 CSS가
 * 항상 펼쳐 보이므로 이 스크립트가 상태를 건드리지 않는다. 안쪽 계정 메뉴는 반대로
 * 768px 미만에서 hidden을 무시하고 항상 펼쳐 보이며(이중 접힘 방지), 그 이상에서
 * 독립된 펼침 메뉴로 동작한다.
 */
export function init() {
    const toggle = byId('btn-nav-toggle');
    const nav = byId('site-nav');
    const accountToggle = byId('btn-account-toggle');
    const accountMenu = byId('account-menu');

    const account = accountToggle && accountMenu
        ? createDisclosure(accountToggle, accountMenu)
        : null;

    if (toggle && nav) {
        // 바깥 메뉴가 닫히면 그 안의 계정 메뉴도 함께 닫는다 — 그렇지 않으면 다음에
        // 바깥 메뉴를 다시 열었을 때 계정 메뉴가 열린 채로 남아 있는 고아 상태가 된다.
        const navDisclosure = createDisclosure(toggle, nav, {
            onClose: () => account?.setOpen(false),
        });

        on(document, 'keydown', (event) => {
            // 계정 메뉴 안에서 연 Bootstrap 모달(비밀번호 변경·회원 탈퇴·인증 메일 재발송)이
            // 열려 있으면 그 모달의 Escape 처리가 우선이다 — 여기서 계정 메뉴까지 함께
            // 닫으면, 모달이 트리거로 되돌리려는 포커스가 그 트리거를 담고 있던 계정 메뉴가
            // 같이 닫히며 사라져 accountToggle로 덮어써진다. Bootstrap 모달의 keydown
            // 리스너는 preventDefault()를 호출하지 않으므로(실측 확인) event.defaultPrevented로는
            // 구분할 수 없다 — 대신 Bootstrap이 모달을 여는 동안 <body>에 붙이는 modal-open
            // 클래스로 "지금 모달이 위에 떠 있는가"를 확인한다.
            if (event.key !== 'Escape' || document.body.classList.contains('modal-open')) {
                return;
            }
            if (account?.isOpen()) {
                account.setOpen(false);
                accountToggle.focus();
            } else if (navDisclosure.isOpen()) {
                navDisclosure.setOpen(false);
                // 닫은 뒤 포커스를 토글로 되돌려야 키보드 사용자가 맥락을 잃지 않는다.
                toggle.focus();
            }
        });
    } else if (account) {
        on(document, 'keydown', (event) => {
            if (event.key === 'Escape' && !document.body.classList.contains('modal-open') && account.isOpen()) {
                account.setOpen(false);
                accountToggle.focus();
            }
        });
    }
}
