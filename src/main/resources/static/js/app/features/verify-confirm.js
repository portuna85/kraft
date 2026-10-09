import { byId, formById, inputById } from '../core/dom.js';

/**
 * 이메일 인증 확인 화면. 메일 링크의 토큰은 URL 프래그먼트(#token=...)로 온다 — 브라우저가 프래그먼트를 서버·프록시로 보내지 않아 접근 로그에 1회용 토큰이 남지 않는다.
 * 여기서 읽어 제출 폼에 채우고 주소창에서는 지운다(브라우저 기록·공유에 남지 않게).
 *
 * 옛 링크(?token=...)는 서버가 이미 hidden input을 채워 렌더하므로 프래그먼트가 없으면 그 값을 그대로 둔다. 어느 쪽에도 토큰이 없으면 제출해도 실패하므로 버튼을 막고 안내를 보여준다.
 */
export function init() {
    const input = inputById('verify-token');
    const form = formById('verify-confirm-form');
    const missing = byId('verify-token-missing');
    if (!input || !form || !missing) {
        return;
    }

    const hashParams = new URLSearchParams(location.hash.slice(1));
    const fromHash = hashParams.get('token');
    if (fromHash) {
        input.value = fromHash;
    }
    if (hashParams.has('token')) {
        history.replaceState(null, '', location.pathname + location.search);
    }

    if (!input.value) {
        form.hidden = true;
        missing.hidden = false;
    }
}
