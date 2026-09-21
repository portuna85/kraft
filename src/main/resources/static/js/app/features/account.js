import { byId, on, rawValueOf, setBusy, setText } from '../core/dom.js';
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
    initWithdraw();
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
 * 폼의 submit에 건다 — 예전처럼 버튼 click에만 걸면 Enter로 제출할 수 없고 required·minlength도
 * 걸리지 않는다(개선 보고서 사용성 항목). 제출 버튼은 모달 푸터에 있어 form 속성으로 연결된다.
 *
 * 오류는 화면 이동이 없으니 배너가 아니라 모달 안에서 보여준다(모달이 #flash를 덮는다).
 * 성공하면 로그인 화면으로 보낸다 — 로그아웃을 여기서 하지 않는 이유는 서버가 변경을 커밋한 뒤
 * 이 계정의 모든 세션을 이미 폐기했기 때문이다(UserService.changePassword). 예전처럼 JS가
 * 이어서 /logout을 호출하면 이미 없는 세션 때문에 CSRF·세션 검사에 걸린다.
 */
// 모달을 열 때마다 올린다. 요청 시작 시점의 값을 스냅샷 떠 두면, 응답이 왔을 때 사용자가
// 이미 모달을 닫고 다시 열어(폼을 reset한) 새 시도를 시작했는지 구분할 수 있다 — 낡은 실패를
// 방금 새로 연 폼 위에 덮어씌우지 않는다(개선 보고서 F10). 성공 시의 이동은 세대와 무관하게
// 항상 실행한다 — 서버가 이미 세션을 폐기했으므로 화면 상태와 무관하게 반드시 옮겨야 한다.
let changePasswordGeneration = 0;

function initChangePassword() {
    const element = byId('changePasswordModal');
    if (!element) {
        return;
    }

    const form = byId('change-password-form');
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

async function changePassword(openedAt) {
    const button = byId('btn-change-password');
    // disabled 버튼은 클릭은 막아도 같은 폼 안 입력창에서 Enter를 누른 submit까지 막지는
    // 않는다 — 이미 진행 중이면 함수 자체가 재진입을 거부해야 한다(개선 보고서 F10).
    if (button.disabled) {
        return;
    }
    setBusy(button, true);
    hideModalError();

    try {
        await api.put('/api/v1/users/me/password', {
            currentPassword: rawValueOf(byId('currentPassword')),
            newPassword: rawValueOf(byId('newPassword')),
        });
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
 * 회원 탈퇴 모달. 되돌릴 수 없는 작업이라 비밀번호를 한 번 더 받는다.
 *
 * 비밀번호 변경과 같은 자리에서 같은 규칙을 쓴다 — 오류는 화면 이동이 없으니 모달 안에서
 * 보여주고(모달이 #flash를 덮는다), 성공하면 서버가 이미 세션을 폐기했으므로 로그인 화면으로
 * 보낸다. 탈퇴 안내는 그 화면에서 flash로 한 번 보인다.
 */
// changePasswordGeneration과 같은 이유(개선 보고서 F10).
let withdrawGeneration = 0;

function initWithdraw() {
    const element = byId('withdrawModal');
    if (!element) {
        return;
    }

    const form = byId('withdraw-form');
    on(form, 'submit', (event) => {
        event.preventDefault();
        withdraw(withdrawGeneration);
    });

    on(element, 'show.bs.modal', () => {
        withdrawGeneration += 1;
        form.reset();
        hideWithdrawError();
    });
    on(element, 'shown.bs.modal', () => byId('withdrawPassword').focus());
}

function showWithdrawError(message) {
    const box = byId('withdraw-error');
    setText(box, message);
    box.hidden = false;
}

function hideWithdrawError() {
    const box = byId('withdraw-error');
    setText(box, '');
    box.hidden = true;
}

async function withdraw(openedAt) {
    const button = byId('btn-confirm-withdraw');
    // changePassword()와 같은 이유(개선 보고서 F10).
    if (button.disabled) {
        return;
    }
    setBusy(button, true);
    hideWithdrawError();

    try {
        await api.del('/api/v1/users/me', {
            currentPassword: rawValueOf(byId('withdrawPassword')),
        });
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
 * 인증 메일 재발송 모달. 버튼을 누르는 즉시 메일이 나가던 것을 한 번 확인받도록 바꿨다 —
 * 재발송은 이전 토큰을 무효로 만들기 때문이다.
 * <p>
 * changePassword·withdraw와 달리 generation 가드를 두지 않는다(F05 검토 결과). 그 둘의
 * generation은 "모달을 닫고 다시 열었는데 이전 시도의 오류가 새 폼 위에 남는 것"을 막는데,
 * 이 모달은 입력 필드가 없고 오류를 모달 안이 아니라 전역 토스트로만 보여줘 다시 열어도
 * 남을 "이전 폼 상태" 자체가 없다. setBusy(false)만 재진입을 허용하면 충분하다.
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
