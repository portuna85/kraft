import { createApp } from 'vue';
import SignupApp from './SignupApp.vue';

/**
 * 회원가입 Vue 아일랜드의 진입점. 다른 아일랜드와 같은 규칙: 마운트 지점이 없는 페이지에서는
 * 조용히 아무 일도 하지 않는다. 초기 상태로 받을 값은 없다 — 빈 폼에서 시작한다.
 */
const mountPoint = document.getElementById('signup-app');

if (mountPoint) {
    createApp(SignupApp).mount(mountPoint);
}
