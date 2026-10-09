import { byId, must, on, setText, buttonById } from '../core/dom.js';
import { modal } from '../core/bootstrap-ui.js';
import { messageOf } from '../core/http.js';
import { showToast } from './toast.js';

/**
 * 공용 #confirmDeleteModal로 확인을 받는 단 하나의 컨트롤러. 되돌릴 수 없는 조작(게시글·댓글 삭제, 관리자 삭제) 전에 쓴다. 핸들러를 하나로 합쳐, 두 사용 방식을 이 모듈이 맡는다.
 *
 * <ul>
 *   <li>확인만 받는 방식: 확인을 누르면 곧바로 모달을 닫고 true를 해결한다.</li>
 *   <li>{@code run}을 넘기는 방식: 확인을 누르면 모달을 연 채 버튼을 잠그고 run을 기다린 뒤 닫는다. 실패하면 토스트로 알린다. 서버 응답을 기다리는 동안 같은 대상을 두 번 확인하지 못하게 한다.</li>
 * </ul>
 */

/**
 * @typedef {object} Pending
 * @property {(result: boolean) => void} resolve
 * @property {HTMLElement | null} trigger
 * @property {string | undefined} fallbackFocusId
 * @property {(() => Promise<unknown>) | undefined} run
 * @property {boolean | undefined} outcome run이 끝난 결과. 모달이 닫힐 때 이 값으로 해결한다.
 */

/** @type {Pending | null} */
let pending = null;
let listenersBound = false;

// 모달을 열 때마다 올린다. 확인 버튼을 누른 시점의 값을 스냅샷 떠 두면 run이 끝났을 때 이미 닫고 다른 대상을 열었는지 구분할 수 있다("지금 보이는 모달을 닫아도 되는가"는 이 세대로만 판단).
let generation = 0;

function bindListenersOnce() {
    if (listenersBound) {
        return;
    }
    listenersBound = true;

    on(buttonById('btn-confirm-delete'), 'click', onConfirmClick);

    // 확인 버튼 클릭도 모달을 hide()하므로 이 이벤트를 거치지만, 그때는 이미 settle(true)로 pending이 비어 settle(false)는 아무 일도 하지 않는다. 취소·ESC·바깥 클릭과 run 방식 종료 때만 여기서 해결된다.
    on(byId('confirmDeleteModal'), 'hidden.bs.modal', () => {
        settle(false);
    });
}

async function onConfirmClick() {
    const button = must(buttonById('btn-confirm-delete'), 'btn-confirm-delete');
    if (!pending || button.disabled) {
        return;
    }
    const current = pending;
    if (!current.run) {
        settle(true);
        modal('#confirmDeleteModal').hide();
        return;
    }

    const openedAt = generation;
    button.disabled = true;
    try {
        await current.run();
        current.outcome = true;
    } catch (error) {
        // 되돌아갈 폼이 없는 배경 동작이라 토스트로 알린다(ui/flash.js의 규칙 참고).
        showToast(messageOf(error), 'danger');
    } finally {
        // 그사이 닫고 다른 대상을 열었다면(세대가 바뀌었다면) 그 새 대화상자는 건드리지 않는다 — 낡은 세대의 완료가 새 요청의 disabled를 풀면 안 된다.
        if (openedAt === generation) {
            button.disabled = false;
            modal('#confirmDeleteModal').hide();
        }
    }
}

function settle(result) {
    if (!pending) {
        return;
    }
    const { resolve, trigger, fallbackFocusId, outcome } = pending;
    pending = null;
    resolve(outcome ?? result);
    // 키보드 사용자가 닫은 뒤 원래 누르던 버튼으로 돌아가야 위치를 잃지 않는다. 트리거가 목록 갱신으로 사라졌을 수 있다(댓글 삭제 성공 경로) — 사라진 요소의 focus()는 무시되므로 늘 존재하는 대체 위치로 옮긴다.
    if (trigger && document.contains(trigger)) {
        trigger.focus();
    } else if (fallbackFocusId) {
        byId(fallbackFocusId)?.focus();
    }
}

/**
 * @param {object} options
 * @param {string} options.title
 * @param {string} options.message
 * @param {string} [options.confirmLabel]
 * @param {HTMLElement | null} [options.trigger] 닫은 뒤 포커스를 돌려줄 요소. 없으면 지금 포커스된 요소.
 * @param {string} [options.fallbackFocusId] trigger가 사라졌을 때 포커스를 줄 요소의 id.
 * @param {() => Promise<unknown>} [options.run] 있으면 확인 뒤 모달을 연 채 실행하고 끝나면 닫는다.
 * @returns {Promise<boolean>} 확인했으면 true(run이 있으면 run이 성공했을 때), 취소·ESC·바깥 클릭·실패면 false.
 */
export function confirmAction({ title, message, confirmLabel = '확인', trigger, fallbackFocusId, run }) {
    bindListenersOnce();
    // 정상 호출부는 await로 앞선 확인이 끝난 뒤에만 다음을 연다. 남은 이전 pending이 있으면 false로 정리하고 새로 연다.
    settle(false);
    generation += 1;

    const focusTarget = trigger ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);

    setText(byId('confirmDeleteModalLabel'), title);
    setText(byId('confirmDeleteMessage'), message);
    const confirmButton = must(buttonById('btn-confirm-delete'), 'btn-confirm-delete');
    confirmButton.textContent = confirmLabel;
    // 이전 대상의 요청이 진행 중이어도 서로 다른 대상이면 동시에 처리해도 무방하다 — 새 대화상자는 곧바로 확인할 수 있어야 한다.
    confirmButton.disabled = false;

    return new Promise((resolve) => {
        pending = { resolve, trigger: focusTarget, fallbackFocusId, run, outcome: undefined };
        modal('#confirmDeleteModal').show();
    });
}
