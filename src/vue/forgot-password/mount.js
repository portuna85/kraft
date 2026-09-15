import { createApp } from 'vue';
import ForgotPasswordApp from './ForgotPasswordApp.vue';

/** 비밀번호 찾기 화면의 Vue 아일랜드 진입점. 마운트 지점이 없으면 조용히 아무 일도 하지 않는다. */
const mountPoint = document.getElementById('forgot-password-app');

if (mountPoint) {
    createApp(ForgotPasswordApp).mount(mountPoint);
}
