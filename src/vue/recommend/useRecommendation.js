// @ts-check
import { computed, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';

/**
 * @typedef {Object} RecommendationItem
 * @property {number} position
 * @property {number[]} numbers
 * @property {number|null} score
 * @property {string[]} explanationCodes
 */
/**
 * @typedef {Object} RecommendationResponse
 * @property {string} strategy
 * @property {string} algorithmVersion
 * @property {number} historyThroughRound
 * @property {boolean} historicalExclusionApplied
 * @property {string} exclusionPolicyVersion
 * @property {RecommendationItem[]} items
 */

// 화면은 옵션 없이 항상 같은 조건으로 요청한다 — 과거 1등 조합 제외는 서버가 모든 전략에
// 항상 적용한다. API 자체는 전략·고정·제외 파라미터를 그대로 받는다.
const FIXED_REQUEST = Object.freeze({
    strategy: 'reduce_shared_winner_risk',
    count: 5,
    lockedNumbers: [],
    excludedNumbers: [],
});

/**
 * 번호 추천 화면의 상태와 생성 흐름을 담는다. 응답 순서 번호로, 늦게 도착한 이전 요청의
 * 결과를 무시한다.
 */
export function useRecommendation() {
    const status = ref('idle'); // idle | generating | ready | history-not-ready | error
    /** @type {import('vue').Ref<RecommendationResponse|null>} */
    const result = ref(null);
    /** @type {import('vue').Ref<string|null>} */
    const errorMessage = ref(null);
    const liveAnnouncement = ref('');

    let requestSeq = 0;

    const canSubmit = computed(() => status.value !== 'generating');

    async function generate() {
        if (!canSubmit.value) {
            return;
        }

        const seq = ++requestSeq;
        status.value = 'generating';
        errorMessage.value = null;
        liveAnnouncement.value = '추천 번호를 생성하는 중입니다.';

        try {
            /** @type {RecommendationResponse} */
            const response = await api.post(API.NUMBERS_RECOMMEND, { ...FIXED_REQUEST });

            if (seq !== requestSeq) {
                return; // 더 최근 요청이 이미 진행 중이다 — 이 응답은 버린다.
            }

            result.value = response;
            status.value = 'ready';
            liveAnnouncement.value = `추천 조합 ${response.items.length}개를 생성했습니다.`;
        } catch (error) {
            if (seq !== requestSeq) {
                return;
            }

            /** @type {{ body?: { code?: string } }} */
            const apiError = error ?? {};
            if (apiError.body?.code === 'RECOMMENDATION_HISTORY_NOT_READY') {
                status.value = 'history-not-ready';
                errorMessage.value = null;
                liveAnnouncement.value = '추천 이력이 아직 준비되지 않았습니다.';
                return;
            }

            // ProblemDetail의 detail은 이미 한국어 사용자 문구다(ApiExceptionHandler). 본문이 없는
            // 403(CSRF·세션 만료)은 http.js가 고정 안내 문구를 채워 준다.
            status.value = 'error';
            errorMessage.value = messageOf(error);
            // 오류는 화면의 role="alert"가 낭독하므로 polite 영역에는 싣지 않는다(FE-34).
        }
    }

    return {
        status,
        result,
        errorMessage,
        liveAnnouncement,
        canSubmit,
        generate,
    };
}
