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

    element.classList.remove('bg-success', 'text-white', 'bg-danger');
    if (type === 'success') {
        element.classList.add('bg-success', 'text-white');
        setText(byId('app-toast-title'), '완료');
    } else if (type === 'danger') {
        element.classList.add('bg-danger', 'text-white');
        setText(byId('app-toast-title'), '오류');
    } else {
        setText(byId('app-toast-title'), '알림');
    }

    setText(byId('app-toast-body'), message);
    const handle = toast('#app-toast');
    handle.show();

    // Bootstrap을 못 불러왔으면 위 show()는 아무 일도 하지 않아 화면에 아무것도 뜨지 않는다
    // (F05). 오류만은 flash 배너(Bootstrap JS 없이도 동작)로 대신 알린다 — 성공·안내
    // 토스트까지 배너로 옮기면 화면 맨 위로 시선을 계속 끌어 원래 규칙(ui/flash.js)과
    // 어긋난다.
    if (handle === NOOP_HANDLE && type === 'danger') {
        flash.showError(message);
    }
}
