import { createApp } from 'vue';
import RecommendApp from './RecommendApp.vue';

/**
 * 번호 추천 Vue 아일랜드의 진입점. 다른 아일랜드와 같은 규칙: 마운트 지점이 없는 페이지에서는
 * 조용히 아무 일도 하지 않는다. 초기 상태로 받을 값은 없다 — 사용자가 생성 버튼을 눌러야
 * 서버에 요청한다.
 */
const mountPoint = document.getElementById('recommend-app');

if (mountPoint) {
    createApp(RecommendApp).mount(mountPoint);
}
