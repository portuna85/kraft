import { byId, on, setBusy, setText, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { modal } from '../core/bootstrap-ui.js';
import { showToast } from '../ui/toast.js';
import * as flash from '../ui/flash.js';

/**
 * 헤더에 붙은 계정 기능들 — 로그아웃, 비밀번호 변경 모달, 인증 메일 재발송 모달.
 * 서로 공유하는 로직은 없지만 모두 layout/navbar에서 시작하고 생명주기가 같아 한곳에 둔다.
 */
export function init() {
    initLogout();
    initChangePassword();
    initResendVerification();
}

function initLogout() {
    const button = byId('btn-logout');
    const form = byId('logout-form');
    if (!button || !form) {
        return;
    }

    // requestSubmit()이 아니라 submit()을 쓴다 — 전자는 Safari 16+이고 이 프로젝트의 지원
    // 범위는 iOS 15부터다. 이 폼에는 submit 핸들러도 검증할 입력도 없어 차이가 없다.
    on(button, 'click', () => form.submit());
}

/**
 * 비밀번호 변경 모달. 별도 화면이 없어졌으므로 어느 화면에서든 헤더의 "비밀번호 변경"으로 열린다.
 *
 * 오류는 화면 이동이 없으니 배너가 아니라 모달 안에서 보여준다(모달이 #flash를 덮는다).
 * 성공하면 로그인 화면으로 보낸다 — 로그아웃을 여기서 하지 않는 이유는 서버가 변경을 커밋한 뒤
 * 이 계정의 모든 세션을 이미 폐기했기 때문이다(UserService.changePassword). 예전처럼 JS가
 * 이어서 /logout을 호출하면 이미 없는 세션 때문에 CSRF·세션 검사에 걸린다.
 */
function initChangePassword() {
    const element = byId('changePasswordModal');
    if (!element) {
        return;
    }

    on(byId('btn-change-password'), 'click', changePassword);

    // Bootstrap 5가 쏘는 실제 DOM 이벤트라 addEventListener로 그대로 받는다.
    on(element, 'show.bs.modal', () => {
        byId('change-password-form').reset();
        hideModalError();
    });
    on(element, 'shown.bs.modal', () => byId('currentPassword').focus());
}

function showModalError(message) {
    const box = byId('change-password-error');
    setText(box, message);
    box.hidden = false;
}

function hideModalError() {
    const box = byId('change-password-error');
    setText(box, '');
    box.hidden = true;
}

async function changePassword() {
    const button = byId('btn-change-password');
    setBusy(button, true);
    hideModalError();

    try {
        await api.put('/api/v1/users/me/password', {
            currentPassword: valueOf(byId('currentPassword')),
            newPassword: valueOf(byId('newPassword')),
        });
        flash.set('PASSWORD_CHANGED');
        window.location.href = '/login';
    } catch (error) {
        showModalError(messageOf(error));
        setBusy(button, false);
    }
}

/**
 * 인증 메일 재발송 모달. 버튼을 누르는 즉시 메일이 나가던 것을 한 번 확인받도록 바꿨다 —
 * 재발송은 이전 토큰을 무효로 만들기 때문이다.
 */
function initResendVerification() {
    const button = byId('btn-confirm-resend');
    if (!button) {
        return;
    }

    on(button, 'click', async () => {
        setBusy(button, true);
        try {
            await api.post('/api/v1/users/me/verify-email/resend');
            showToast('인증 메일을 다시 보냈습니다. 메일함을 확인해 주세요.', 'success');
        } catch (error) {
            showToast(messageOf(error), 'danger');
        } finally {
            setBusy(button, false);
            modal('#resendVerificationModal').hide();
        }
    });
}
