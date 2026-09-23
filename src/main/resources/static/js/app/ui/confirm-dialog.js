import { byId, on, setText } from '../core/dom.js';
import { modal } from '../core/bootstrap-ui.js';

/**
 * 되돌릴 수 없는 관리자 조작(삭제·삭제+정지) 전에 공용 #confirmDeleteModal로 확인을 받는다
 * (개선 보고서 SEC-05·FE-C1). delete-confirm.js와 같은 모달 DOM을 재사용하지만, 이 모듈이
 * 로드되는 화면(관리자, `.report-list`가 있는 페이지)에는 delete-confirm.js가 로드되지
 * 않으므로 이벤트 리스너가 겹치지 않는다(main.js의 loadIf 조건 참고). 그래서 여기서는
 * delete-confirm.js의 kind별(post/comment) 분기 없이, 호출부가 문구까지 직접 넘기는 더
 * 일반적인 형태로 만든다.
 */

/** @type {{ resolve: (result: boolean) => void, trigger: HTMLElement | null } | null} */
let pending = null;
let listenersBound = false;

function bindListenersOnce() {
    if (listenersBound) {
        return;
    }
    listenersBound = true;

    on(byId('btn-confirm-delete'), 'click', () => {
        settle(true);
        modal('#confirmDeleteModal').hide();
    });

    // 확인 버튼 클릭도 결국 모달을 hide()하므로 이 이벤트를 거친다 — 그때는 이미 위에서
    // settle(true)로 pending이 비어 있어(clear) settle(false)가 아무 일도 하지 않는다.
    // 취소 버튼·ESC·바깥 클릭으로 닫힌 경우에만 실제로 false를 해결한다.
    on(byId('confirmDeleteModal'), 'hidden.bs.modal', () => {
        settle(false);
    });
}

function settle(result) {
    if (!pending) {
        return;
    }
    const { resolve, trigger } = pending;
    pending = null;
    resolve(result);
    // 키보드 사용자가 확인 대화상자를 닫은 뒤 원래 누르던 버튼으로 되돌아가야 위치를
    // 잃지 않는다(delete-confirm.js와 같은 이유). 그사이 페이지가 다시 그려져 트리거가
    // 사라졌을 수 있으니 존재를 확인한다.
    if (trigger && document.contains(trigger)) {
        trigger.focus();
    }
}

/**
 * @param {{ title: string, message: string, confirmLabel?: string }} options
 * @returns {Promise<boolean>} 확인을 누르면 true, 취소·ESC·바깥 클릭이면 false.
 */
export function confirmAction({ title, message, confirmLabel = '확인' }) {
    bindListenersOnce();
    // 정상 호출부는 항상 await로 앞선 확인이 끝난 뒤에만 다음 것을 연다. 그래도 혹시 남아
    // 있는 이전 pending이 있다면 false로 정리하고 새로 연다 — 열린 채로 버려두지 않는다.
    settle(false);

    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;

    setText(byId('confirmDeleteModalLabel'), title);
    setText(byId('confirmDeleteMessage'), message);
    const confirmButton = byId('btn-confirm-delete');
    confirmButton.textContent = confirmLabel;
    confirmButton.disabled = false;

    return new Promise((resolve) => {
        pending = { resolve, trigger };
        modal('#confirmDeleteModal').show();
    });
}
