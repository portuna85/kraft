import { byId, setText } from '../core/dom.js';
import { NOOP_HANDLE, toast } from '../core/bootstrap-ui.js';
import * as flash from './flash.js';

/**
 * 잠깐 떴다 사라지는 알림.
 *
 * 언제 토스트를 쓰고 언제 지속 배너(flash)를 쓰는지는 ui/flash.js의 규칙 참고.
 *
 * @param {string} message
 * @param {'success' | 'danger' | 'info' | string} [type]
 */
export function showToast(message, type) {
    const element = byId('app-toast');
    if (!element) {
        return;
    }

    // 오류 토스트는 사용자가 닫을 때까지 남는다(아래). 그 위에 다른 토스트를 덮어쓰면 오류가 읽히기 전에 사라지므로 닫힐 때까지 줄을 세운다.
    if (dangerShowing) {
        queue.push({ message, type });
        return;
    }
    display(element, message, type);
}

/** 오류 토스트가 떠 있는 동안 도착한 알림. 그 토스트가 닫히면 순서대로 꺼낸다. */
const queue = /** @type {{ message: string, type?: string }[]} */ ([]);
let dangerShowing = false;
let listening = false;

/**
 * @param {HTMLElement} element
 * @param {string} message
 * @param {string} [type]
 */
function display(element, message, type) {

    element.classList.remove('bg-success', 'text-white', 'bg-danger');
    // 오류 토스트는 읽고 조치해야 할 내용이라, 3초 뒤 자동으로 사라지는 기본 동작(role="status"/aria-live="polite")으로는 스크린 리더가 놓치거나 눈으로 보던 사람도 다 읽기 전에 사라질 수 있다.
    // role="alert"/aria-live="assertive"로 즉시 announce하고 autohide를 꺼 직접 닫을 때까지 남겨 둔다. 다른 타입은 기본값(footer.html의 정적 마크업)으로 되돌린다.
    const isDanger = type === 'danger';
    element.setAttribute('role', isDanger ? 'alert' : 'status');
    element.setAttribute('aria-live', isDanger ? 'assertive' : 'polite');
    if (type === 'success') {
        element.classList.add('bg-success', 'text-white');
        setText(byId('app-toast-title'), '완료');
    } else if (isDanger) {
        element.classList.add('bg-danger', 'text-white');
        setText(byId('app-toast-title'), '오류');
    } else {
        setText(byId('app-toast-title'), '알림');
    }

    setText(byId('app-toast-body'), message);
    const handle = toast('#app-toast', isDanger ? { autohide: false } : { autohide: true, delay: 3000 });
    dangerShowing = isDanger && handle !== NOOP_HANDLE;
    if (dangerShowing && !listening) {
        listening = true;
        element.addEventListener('hidden.bs.toast', () => {
            dangerShowing = false;
            const next = queue.shift();
            if (next) {
                showToast(next.message, next.type);
            }
        });
    }
    handle.show();

    // Bootstrap을 못 불러왔으면 위 show()는 아무 일도 하지 않아 화면에 아무것도 뜨지 않는다. 오류만은 Bootstrap JS 없이도 동작하는 flash 배너로 대신 알린다
    // (성공·안내 토스트까지 옮기면 시선을 화면 맨 위로 계속 끌어 ui/flash.js의 규칙과 어긋난다).
    if (handle === NOOP_HANDLE && type === 'danger') {
        flash.showError(message);
    }
}
