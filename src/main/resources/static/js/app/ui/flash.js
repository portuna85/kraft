import { byId, setText } from '../core/dom.js';

/**
 * 두 가지 역할을 한다: 화면을 옮긴 뒤 목적지에서 한 번만 보여주는 결과 메시지(sessionStorage
 * 전달)와, 이동 없이 그 자리에서 계속 보이는 오류 요약.
 *
 * ── 토스트와 배너를 가르는 규칙 ─────────────────────────────────────────────
 * 실패가 **사용자가 고쳐서 다시 시도해야 하는 폼에 붙어 있으면** 지속 배너(showError),
 * 그렇지 않으면 사라지는 토스트(showToast)를 쓴다.
 *
 * 근거: 재시도 안내는 한 문장으로 끝나지 않는다("이미지는 이미 업로드되어 있으니 다시
 * 누르면 같은 이미지로 재시도합니다"). 사용자가 무엇을 할지 정하는 동안 사라지면 안 된다.
 *
 * 예외 두 가지:
 *  - 댓글 등록·수정 실패는 폼이 있어도 토스트를 쓴다. 댓글 폼은 본문 아래쪽에 있고 #flash는
 *    화면 맨 위라, 배너를 띄우면 사용자가 보고 있지 않은 곳에 뜬다.
 *  - 비밀번호 변경 실패는 모달 안(#change-password-error)에 남긴다. 화면 이동이 없고 모달이
 *    #flash를 덮기 때문이다.
 * ───────────────────────────────────────────────────────────────────────────
 *
 * 허용된 키만 저장·표시한다 — 비밀번호·본문·토큰 같은 사용자 입력은 절대 넣지 않는다.
 * sessionStorage를 쓸 수 없는 환경(예외를 던지는 브라우저 설정)에서도 이동 자체는 정상
 * 동작해야 하므로 모든 접근을 try/catch로 감싼다.
 */

const STORAGE_KEY = 'kraft.flash';

const MESSAGES = {
    POST_SAVED: { text: '글이 등록되었습니다.', type: 'success' },
    POST_UPDATED: { text: '글이 수정되었습니다.', type: 'success' },
    POST_DELETED: { text: '글이 삭제되었습니다.', type: 'success' },
    COMMENT_SAVED: { text: '댓글이 등록되었습니다.', type: 'success' },
    COMMENT_UPDATED: { text: '댓글이 수정되었습니다.', type: 'success' },
    COMMENT_DELETED: { text: '댓글이 삭제되었습니다.', type: 'success' },
    SIGNUP_DONE: { text: '가입이 완료되었습니다. 이메일 인증 안내를 확인해 주세요.', type: 'success' },
    PASSWORD_CHANGED: { text: '비밀번호가 변경되었습니다. 다시 로그인해 주세요.', type: 'success' },
};

/** 이동 후 목적 화면에서 한 번 보여줄 메시지를 예약한다. */
export function set(key) {
    if (!MESSAGES[key]) {
        return;
    }
    try {
        sessionStorage.setItem(STORAGE_KEY, key);
    } catch {
        // 저장소를 쓸 수 없어도 이동은 정상적으로 진행된다.
    }
}

/** 예약된 메시지가 있으면 표시하고 지운다. 각 페이지 로드 시 한 번 호출한다. */
export function consume() {
    let key = null;
    try {
        key = sessionStorage.getItem(STORAGE_KEY);
        if (key) {
            sessionStorage.removeItem(STORAGE_KEY);
        }
    } catch {
        return;
    }

    const entry = key ? MESSAGES[key] : null;
    if (entry) {
        render(entry.text, entry.type === 'danger');
    }
}

/**
 * 이동 없이 즉시 오류를 띄운다. role을 alert로 올려 보조기기가 바로 읽게 한다
 * (성공 메시지는 status/polite를 유지한다).
 */
export function showError(text) {
    render(text, true);
}

/**
 * 사용자가 원인을 스스로 고친 뒤(예: 파일을 다시 선택)에도 이전 오류가 남아 있으면 이미
 * 해결된 문제처럼 보이지 않게 감춘다. 다음 실패에서 render()가 새로 채운다.
 */
export function hide() {
    const flash = byId('flash');
    if (flash) {
        flash.hidden = true;
    }
}

function render(text, isError) {
    const flash = byId('flash');
    if (!flash) {
        return;
    }

    setText(byId('flash-text'), text);
    flash.classList.toggle('flash--danger', Boolean(isError));
    flash.setAttribute('role', isError ? 'alert' : 'status');
    flash.hidden = false;

    // 긴 폼에서는 화면 맨 위의 배너가 시야 밖일 수 있다. 저장 실패를 못 보고 다시 누르는 일을 막는다.
    if (isError) {
        flash.scrollIntoView({ block: 'nearest' });
    }
}
