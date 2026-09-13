import { byId, setText } from '../core/dom.js';
import { toast } from '../core/bootstrap-ui.js';

/**
 * 잠깐 떴다 사라지는 알림.
 *
 * 언제 토스트를 쓰고 언제 지속 배너(flash)를 쓰는지는 ui/flash.js의 규칙 참고.
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
    toast('#app-toast').show();
}
