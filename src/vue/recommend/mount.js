import { mountIsland } from '../shared/mountIsland.js';
import RecommendApp from './RecommendApp.vue';

/**
 * 번호 추천 Vue 아일랜드의 진입점. 부트스트랩 JSON은 없다 — 사용자가 생성 버튼을 눌러야 서버에 요청한다. 서버가
 * 렌더링 시점에 아는 값 하나(검증된 이력의 마지막 회차)만 `data-history-round`로 받아 안내 문구에 쓴다.
 */
const mountPoint = document.getElementById('recommend-app');
const historyRound = Number(mountPoint?.dataset.historyRound);

mountIsland({
    mountPoint,
    component: RecommendApp,
    props: { historyRound: Number.isFinite(historyRound) && historyRound > 0 ? historyRound : null },
});
