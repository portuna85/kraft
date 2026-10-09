import { mountIsland } from '../shared/mountIsland.js';
import PasswordResetApp from './PasswordResetApp.vue';

/**
 * 비밀번호 재설정 화면의 Vue 아일랜드 진입점.
 *
 * 토큰은 URL 프래그먼트(#token=...)로 온다 — 쿼리 문자열과 달리 브라우저가
 * 프래그먼트를 서버로 보내지 않으므로 nginx 접근 로그·서버 로그에 1회용 토큰이 남지 않는다.
 * 읽자마자 history.replaceState로 지워, 주소를 복사·공유하거나 새로고침해도 주소창에 남지
 * 않게 한다.
 *
 * 이 변경 전에 이미 발송된 메일의 옛 링크(?token=...)는 최대 30분(토큰 유효 기간) 동안
 * 계속 열릴 수 있으므로 쿼리 문자열도 하위 호환으로 함께 읽는다.
 */
const hashParams = new URLSearchParams(location.hash.slice(1));
const token = hashParams.get('token') ?? new URLSearchParams(location.search).get('token') ?? '';
if (hashParams.has('token')) {
    history.replaceState(null, '', location.pathname + location.search);
}

const mountPoint = document.getElementById('password-reset-app');
mountIsland({
    mountPoint,
    component: PasswordResetApp,
    props: { token },
});
