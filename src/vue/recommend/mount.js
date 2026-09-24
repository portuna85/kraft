import { mountIsland } from '../shared/mountIsland.js';
import RecommendApp from './RecommendApp.vue';

/**
 * 번호 추천 Vue 아일랜드의 진입점. 초기 상태로 받을 값은 없다 — 사용자가 생성 버튼을 눌러야
 * 서버에 요청한다.
 */
mountIsland({
    mountPoint: document.getElementById('recommend-app'),
    component: RecommendApp,
});
