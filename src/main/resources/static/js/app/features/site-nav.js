import { byId, on, buttonById } from '../core/dom.js';

/**
 * aria-expanded + hidden 속성으로 열림·닫힘을 표현하는 펼침 버튼 하나를 초기화한다. 닫힐 때 실행할 부수 효과(onClose)를 받아, 바깥 메뉴가 닫힐 때 그 안의 계정 메뉴도 함께 닫는 식의 연쇄 동작에 쓴다.
 */
function createDisclosure(toggle, panel, { onClose } = /** @type {{ onClose?: () => void }} */ ({})) {
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
 * 헤더 오른쪽 버튼 모음(바깥 모바일 메뉴)과 그 안의 닉네임 계정 펼침 메뉴를 초기화한다. 바깥은 768px 미만에서만 hidden 속성으로 열림·닫힘을 표현하고(그 이상은 CSS가 항상 펼친다),
 * 안쪽 계정 메뉴는 반대로 768px 미만에서 hidden을 무시하고 항상 펼쳐 보이며(이중 접힘 방지) 그 이상에서 독립된 펼침 메뉴로 동작한다.
 */
export function init() {
    const toggle = buttonById('btn-nav-toggle');
    const nav = byId('site-nav');
    const accountToggle = buttonById('btn-account-toggle');
    const accountMenu = byId('account-menu');

    const account = accountToggle && accountMenu
        ? createDisclosure(accountToggle, accountMenu)
        : null;

    if (toggle && nav) {
        // 서버는 펼친 채로 내려보낸다. 여기서 접으면서 CSS의 "준비 전 임시 접힘"을 이어받는다.
        nav.hidden = true;
        document.documentElement.classList.add('js-ready');

        // 바깥 메뉴가 닫히면 그 안의 계정 메뉴도 함께 닫는다 — 아니면 다음에 바깥 메뉴를 열었을 때 계정 메뉴가 열린 채로 남는다.
        const navDisclosure = createDisclosure(toggle, nav, {
            onClose: () => account?.setOpen(false),
        });

        on(document, 'keydown', /** @param {KeyboardEvent} event */ (event) => {
            // 계정 메뉴 안에서 연 Bootstrap 모달(비밀번호 변경·회원 탈퇴·인증 메일 재발송)이 열려 있으면 그 모달의 Escape 처리가 우선이다 — 여기서 계정 메뉴까지 닫으면
            // 모달이 포커스를 되돌리려던 트리거가 사라져 accountToggle로 덮어써진다. Bootstrap 모달의 keydown 리스너는 preventDefault()를 호출하지 않아(실측) event.defaultPrevented로는 구분할 수 없으므로,
            // 모달이 열려 있는 동안 <body>에 붙는 modal-open 클래스로 판단한다.
            if (event.key !== 'Escape' || document.body.classList.contains('modal-open')) {
                return;
            }
            if (account?.isOpen()) {
                account.setOpen(false);
                accountToggle?.focus();
            } else if (navDisclosure.isOpen()) {
                navDisclosure.setOpen(false);
                // 닫은 뒤 포커스를 토글로 되돌려야 키보드 사용자가 맥락을 잃지 않는다.
                toggle.focus();
            }
        });
    } else if (account) {
        on(document, 'keydown', /** @param {KeyboardEvent} event */ (event) => {
            if (event.key === 'Escape' && !document.body.classList.contains('modal-open') && account.isOpen()) {
                account.setOpen(false);
                accountToggle?.focus();
            }
        });
    }
}
