// @ts-check
import { computed, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API } from '@core/constants.js';
import {
    DEFAULT_COUNT,
    DEFAULT_STRATEGY,
    HISTORY_NOT_READY_CODE,
    buildRequest,
    describeFailure,
} from './recommendRequest.js';

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

/**
 * 번호 추천 화면의 상태와 생성 흐름을 담는다. 추천 방식·개수는 화면이 고른 값을 그대로 요청에 싣는다
 * ({@link buildRequest}). 응답 순서 번호로, 늦게 도착한 이전 요청의 결과를 무시한다.
 */
export function useRecommendation() {
    const status = ref('idle'); // idle | generating | ready | history-not-ready | error
    const strategy = ref(DEFAULT_STRATEGY);
    const count = ref(DEFAULT_COUNT);
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
            const response = await api.post(API.NUMBERS_RECOMMEND, buildRequest({
                strategy: strategy.value,
                count: Number(count.value),
            }));

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
            if (apiError.body?.code === HISTORY_NOT_READY_CODE) {
                status.value = 'history-not-ready';
                errorMessage.value = null;
                liveAnnouncement.value = '추천 이력이 아직 준비되지 않았습니다.';
                return;
            }

            // ProblemDetail의 detail은 이미 한국어 사용자 문구다(ApiExceptionHandler). 본문이 없는
            // 403(CSRF·세션 만료)은 http.js가 고정 안내 문구를 채워 준다. 사용자가 조치할 수 있는 오류에는
            // 다음 행동을 덧붙인다(describeFailure).
            status.value = 'error';
            errorMessage.value = describeFailure(apiError.body?.code, messageOf(error));
            // 오류는 화면의 role="alert"가 낭독하므로 polite 영역에는 싣지 않는다(FE-34).
        }
    }

    return {
        status,
        strategy,
        count,
        result,
        errorMessage,
        liveAnnouncement,
        canSubmit,
        generate,
    };
}
