import { mountIsland } from '../shared/mountIsland.js';
import ForgotPasswordApp from './ForgotPasswordApp.vue';

/** 비밀번호 찾기 화면의 Vue 아일랜드 진입점. */
mountIsland({
    mountPoint: document.getElementById('forgot-password-app'),
    component: ForgotPasswordApp,
});
