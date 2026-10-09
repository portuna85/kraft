import { byId, must, on, rawValueOf, setBusy, setText, buttonById, formById, inputById } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { API, PASSWORD } from '../core/constants.js';
import { modal } from '../core/bootstrap-ui.js';
import { clearAllDrafts } from '../core/drafts.js';
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
    initWithdraw();
    ['changePasswordModal', 'withdrawModal', 'resendVerificationModal'].forEach(bindFocusReturn);
}

// 세 계정 모달 모두 열 때 누르고 있던 버튼으로 돌아가야 키보드 사용자가 위치를 잃지 않는다(Bootstrap은 트리거 복귀를 해 주지 않아 직접 건다).
function bindFocusReturn(elementId) {
    const element = byId(elementId);
    if (!element) {
        return;
    }
    let trigger = null;
    on(element, 'show.bs.modal', () => {
        trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    });
    on(element, 'hidden.bs.modal', () => {
        if (trigger && document.contains(trigger)) {
            trigger.focus();
        }
        trigger = null;
    });
}

function initLogout() {
    const button = buttonById('btn-logout');
    const form = formById('logout-form');
    if (!button || !form) {
        return;
    }

    // requestSubmit()은 Safari 16+이고 지원 범위가 iOS 15부터라 submit()을 쓴다(이 폼에는 submit 핸들러도 검증할 입력도 없다).
    on(button, 'click', () => {
        // 다음에 이 브라우저로 로그인하는 사람이 볼 수 없게 이 계정의 초안을 지운다(실패해도 로그아웃은 진행되고 초안 손실은 크지 않다).
        clearAllDrafts();
        form.submit();
    });
}

/**
 * 비밀번호 변경 모달(어느 화면에서든 헤더의 "비밀번호 변경"으로 열린다). 폼의 submit에 걸어 Enter 제출과 required·minlength가 동작하고, 제출 버튼은 모달 푸터에서 form 속성으로 연결된다.
 * 오류는 화면 이동이 없으니 모달 안에서 보여주고, 성공하면 로그인 화면으로 보낸다 — 서버가 커밋 후 이 계정의 모든 세션을 이미 폐기했으므로(UserService.changePassword) JS가 /logout을 이어 부르지 않는다.
 */
// 모달을 열 때마다 올린다. 요청 시작 때의 값을 스냅샷 떠 두면 응답이 왔을 때 모달을 닫았다 다시 연 새 시도인지 구분할 수 있다 — 낡은 실패를 새 폼에 덮어씌우지 않는다.
// 성공 시 이동은 세대와 무관하게 항상 한다(서버가 이미 세션을 폐기했다).
let changePasswordGeneration = 0;

function initChangePassword() {
    const element = byId('changePasswordModal');
    if (!element) {
        return;
    }

    // 길이 제한은 서버와 맞춰 둔 PASSWORD 상수 한 곳에서 온다(템플릿에 숫자를 따로 적지 않는다).
    const newPassword = must(inputById('changeNewPassword'), 'changeNewPassword');
    newPassword.minLength = PASSWORD.MIN_LENGTH;
    newPassword.maxLength = PASSWORD.MAX_LENGTH;

    const form = must(formById('change-password-form'), 'change-password-form');
    on(form, 'submit', (event) => {
        event.preventDefault();
        changePassword(changePasswordGeneration);
    });

    // Bootstrap 5가 쏘는 실제 DOM 이벤트라 addEventListener로 그대로 받는다.
    on(element, 'show.bs.modal', () => {
        changePasswordGeneration += 1;
        form.reset();
        hideModalError();
    });
    on(element, 'shown.bs.modal', () => inputById('currentPassword')?.focus());
}

function showModalError(message) {
    const box = must(byId('change-password-error'), 'change-password-error');
    setText(box, message);
    box.hidden = false;
}

function hideModalError() {
    const box = must(byId('change-password-error'), 'change-password-error');
    setText(box, '');
    box.hidden = true;
}

async function changePassword(openedAt) {
    const button = must(buttonById('btn-change-password'), 'btn-change-password');
    // disabled 버튼은 클릭만 막고 입력창에서 Enter로 제출하는 경로는 막지 못하므로 함수 자체가 재진입을 거부한다.
    if (button.disabled) {
        return;
    }
    setBusy(button, true);
    hideModalError();

    try {
        await api.put(`${API.USERS_ME}/password`, {
            currentPassword: rawValueOf(inputById('currentPassword')),
            newPassword: rawValueOf(inputById('changeNewPassword')),
        });
        // 서버가 이미 이 계정의 모든 세션을 폐기했다 — 초안도 함께 지운다.
        clearAllDrafts();
        flash.set('PASSWORD_CHANGED');
        window.location.href = '/login';
    } catch (error) {
        setBusy(button, false);
        if (openedAt === changePasswordGeneration) {
            showModalError(messageOf(error));
        }
    }
}

/**
 * 회원 탈퇴 모달. 되돌릴 수 없어 비밀번호를 한 번 더 받는다. 비밀번호 변경과 같은 규칙이다 — 오류는 모달 안에서, 성공하면(서버가 세션을 폐기했으므로) 로그인 화면으로 보내고 탈퇴 안내는 거기서 flash로 한 번 보인다.
 */
// changePasswordGeneration과 같은 이유.
let withdrawGeneration = 0;

function initWithdraw() {
    const element = byId('withdrawModal');
    if (!element) {
        return;
    }

    const form = must(formById('withdraw-form'), 'withdraw-form');
    on(form, 'submit', (event) => {
        event.preventDefault();
        withdraw(withdrawGeneration);
    });

    on(element, 'show.bs.modal', () => {
        withdrawGeneration += 1;
        form.reset();
        hideWithdrawError();
    });
    on(element, 'shown.bs.modal', () => inputById('withdrawPassword')?.focus());
}

function showWithdrawError(message) {
    const box = must(byId('withdraw-error'), 'withdraw-error');
    setText(box, message);
    box.hidden = false;
}

function hideWithdrawError() {
    const box = must(byId('withdraw-error'), 'withdraw-error');
    setText(box, '');
    box.hidden = true;
}

async function withdraw(openedAt) {
    const button = must(buttonById('btn-confirm-withdraw'), 'btn-confirm-withdraw');
    // changePassword()와 같은 이유.
    if (button.disabled) {
        return;
    }
    setBusy(button, true);
    hideWithdrawError();

    try {
        await api.del(API.USERS_ME, {
            currentPassword: rawValueOf(inputById('withdrawPassword')),
        });
        // 탈퇴 계정의 초안은 되찾을 계정 자체가 없다 — 지운다.
        clearAllDrafts();
        flash.set('ACCOUNT_WITHDRAWN');
        window.location.href = '/login';
    } catch (error) {
        setBusy(button, false);
        if (openedAt === withdrawGeneration) {
            showWithdrawError(messageOf(error));
        }
    }
}

/**
 * 인증 메일 재발송 모달. 재발송이 이전 토큰을 무효로 만들어 한 번 확인받는다. changePassword·withdraw와 달리 generation 가드를 두지 않는다 — 입력 필드가 없고 오류를
 * 모달 안이 아니라 전역 토스트로만 보여줘 다시 열어도 남을 "이전 폼 상태"가 없으며, setBusy(false)만 재진입을 허용하면 충분하다.
 */
function initResendVerification() {
    const button = buttonById('btn-confirm-resend');
    if (!button) {
        return;
    }

    on(button, 'click', async () => {
        setBusy(button, true);
        try {
            await api.post(`${API.USERS_ME}/verify-email/resend`);
            showToast('인증 메일을 다시 보냈습니다. 메일함을 확인해 주세요.', 'success');
        } catch (error) {
            showToast(messageOf(error), 'danger');
        } finally {
            setBusy(button, false);
            modal('#resendVerificationModal').hide();
        }
    });
}
