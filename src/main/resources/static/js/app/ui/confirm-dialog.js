import { byId, must, on, setText, buttonById } from '../core/dom.js';
import { modal } from '../core/bootstrap-ui.js';
import { messageOf } from '../core/http.js';
import { showToast } from './toast.js';

/**
 * 공용 #confirmDeleteModal로 확인을 받는 단 하나의 컨트롤러(FE-14). 되돌릴 수 없는 조작
 * (관리자 삭제·삭제+정지, 게시글·댓글 삭제) 전에 쓴다.
 *
 * 예전에는 delete-confirm.js가 같은 모달 DOM에 자기만의 pending·generation·포커스 복귀 로직과
 * 확인 버튼 핸들러를 따로 달았다. 어느 화면에서 어느 쪽이 로드되는지(main.js의 loadIf)에 기대어
 * 우연히 겹치지 않았을 뿐이라, 핸들러를 하나로 합치고 두 사용 방식을 이 모듈이 모두 맡는다.
 *
 * <ul>
 *   <li>확인만 받는 방식: 확인을 누르면 곧바로 모달을 닫고 true를 해결한다.</li>
 *   <li>{@code run}을 넘기는 방식: 확인을 누르면 모달을 연 채 버튼을 잠그고 run을 기다린 뒤 닫는다.
 *       실패하면 토스트로 알린다. 서버 응답을 기다리는 동안 같은 대상을 두 번 확인하지 못하게 한다.</li>
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

// 모달을 열 때마다 올린다. 확인 버튼을 누른 시점의 값을 스냅샷 떠 두면, run이 끝났을 때 사용자가
// 이미 모달을 닫고 다른 대상을 열었는지(세대가 바뀌었는지) 구분할 수 있다 — run 자체는 호출부가
// 만든 클로저라 항상 맞는 대상에 실행되지만, "지금 화면에 보이는 모달을 닫아도 되는가"는 이 세대로만
// 판단해야 한다.
let generation = 0;

function bindListenersOnce() {
    if (listenersBound) {
        return;
    }
    listenersBound = true;

    on(buttonById('btn-confirm-delete'), 'click', onConfirmClick);

    // 확인 버튼 클릭도 결국 모달을 hide()하므로 이 이벤트를 거친다 — 그때는 이미 settle(true)로
    // pending이 비어 있어(clear) settle(false)가 아무 일도 하지 않는다. 취소 버튼·ESC·바깥 클릭으로
    // 닫힌 경우와, run 방식이 끝나 닫힌 경우에만 여기서 해결된다.
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
        // 그사이 사용자가 닫고 다른 대상을 열었다면(세대가 바뀌었다면) 그 새 대화상자는 건드리지
        // 않는다 — 새로 열릴 때 확인 버튼은 이미 다시 활성화되어 있고, 낡은 세대의 완료가 그 뒤에
        // 시작된 요청의 disabled를 도로 풀면 안 된다.
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
    // 키보드 사용자가 확인 대화상자를 닫은 뒤 원래 누르던 버튼으로 되돌아가야 위치를 잃지 않는다.
    // 그사이 목록이 다시 그려지거나 대상이 지워져 트리거가 사라졌을 수 있다(댓글 삭제 성공 경로는
    // 모달이 완전히 닫히기 전에 그 댓글을 목록에서 지운다, F09) — 사라진 요소에 focus()는 조용히
    // 무시되어 포커스가 body로 떨어지므로, 늘 존재하는 대체 위치가 있으면 거기로 옮긴다.
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
    // 정상 호출부는 항상 await로 앞선 확인이 끝난 뒤에만 다음 것을 연다. 그래도 혹시 남아
    // 있는 이전 pending이 있다면 false로 정리하고 새로 연다 — 열린 채로 버려두지 않는다.
    settle(false);
    generation += 1;

    const focusTarget = trigger ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null);

    setText(byId('confirmDeleteModalLabel'), title);
    setText(byId('confirmDeleteMessage'), message);
    const confirmButton = must(buttonById('btn-confirm-delete'), 'btn-confirm-delete');
    confirmButton.textContent = confirmLabel;
    // 이전 대상의 요청이 아직 진행 중이더라도, 서로 다른 대상이면 동시에 처리해도 무방하다 —
    // 새로 연 대화상자는 그 요청과 독립적으로 곧바로 확인할 수 있어야 한다.
    confirmButton.disabled = false;

    return new Promise((resolve) => {
        pending = { resolve, trigger: focusTarget, fallbackFocusId, run, outcome: undefined };
        modal('#confirmDeleteModal').show();
    });
}
