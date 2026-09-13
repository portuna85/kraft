import { byId, on, setBusy, setText, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import * as flash from '../ui/flash.js';

/**
 * 회원가입 폼.
 *
 * 비밀번호 확인 불일치는 서버에 물어볼 필요가 없는 입력 오류이므로 해당 필드 옆에서 바로
 * 알리고 포커스를 옮긴다. 반면 이메일 중복 같은 서버 판정은 폼 전체에 걸리는 오류라 배너에 띄운다.
 */
export function init() {
    const button = byId('btn-signup');
    if (!button) {
        return;
    }

    on(button, 'click', () => save(button));
}

function clearFieldError() {
    const field = byId('passwordConfirm');
    field.classList.remove('is-invalid');
    field.removeAttribute('aria-invalid');
    setText(byId('passwordConfirm-error'), '');
}

function showFieldError(message) {
    const field = byId('passwordConfirm');
    field.classList.add('is-invalid');
    field.setAttribute('aria-invalid', 'true');
    setText(byId('passwordConfirm-error'), message);
    field.focus();
}

async function save(button) {
    clearFieldError();

    const password = valueOf(byId('password'));
    if (password !== valueOf(byId('passwordConfirm'))) {
        showFieldError('비밀번호가 일치하지 않습니다.');
        return;
    }

    setBusy(button, true);
    try {
        await api.post('/api/v1/users', {
            name: valueOf(byId('name')),
            email: valueOf(byId('email')),
            password,
        });
        flash.set('SIGNUP_DONE');
        window.location.href = '/login';
    } catch (error) {
        flash.showError(messageOf(error));
        setBusy(button, false);
    }
}
