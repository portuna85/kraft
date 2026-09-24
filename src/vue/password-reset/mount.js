import { mountIsland } from '../shared/mountIsland.js';
import PasswordResetApp from './PasswordResetApp.vue';

/**
 * 비밀번호 재설정 화면의 Vue 아일랜드 진입점. 토큰은 서버가 data 속성으로 건넨 값을 쓴다 —
 * 화면이 쿼리 문자열을 직접 파싱하지 않는다.
 */
const mountPoint = document.getElementById('password-reset-app');
mountIsland({
    mountPoint,
    component: PasswordResetApp,
    props: { token: mountPoint?.dataset.token ?? '' },
});
