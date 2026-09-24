import { mountIsland } from '../shared/mountIsland.js';
import SignupApp from './SignupApp.vue';

/**
 * 회원가입 Vue 아일랜드의 진입점. 초기 상태로 받을 값은 없다 — 빈 폼에서 시작한다.
 */
mountIsland({
    mountPoint: document.getElementById('signup-app'),
    component: SignupApp,
});
