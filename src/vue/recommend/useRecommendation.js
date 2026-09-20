// @ts-check
import { computed, reactive, ref } from 'vue';
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
/**
 * @typedef {Object} RecommendationConditions
 * @property {string} strategy
 * @property {number} count
 * @property {number[]} locked
 * @property {number[]} excluded
 */

export const STRATEGIES = [
    {
        value: 'balanced',
        label: '형태 균형',
        description: '홀짝·저고·합계·연속·구간 분산 5가지 형태 기준을 채점해 상위 조합을 고릅니다.',
    },
    {
        value: 'random',
        label: '조건 내 무작위',
        description: '고정·제외·과거 당첨 조합 제외 조건을 지킨 상태에서 무작위로 뽑습니다.',
    },
    {
        value: 'reduce_shared_winner_risk',
        label: '흔한 선택 패턴 피하기',
        description: '생일·기념일처럼 몰리기 쉬운 숫자 선택 패턴을 피하려는 규칙을 적용합니다.',
    },
];

/** @type {Record<string, string>} */
const EXPLANATION_LABELS = {
    ODD_EVEN_BALANCED: '홀짝 균형',
    LOW_HIGH_BALANCED: '저고 균형',
    SUM_IN_RANGE: '합계 100~180',
    CONSECUTIVE_PAIR_LIMITED: '연속 번호 제한',
    DECADE_SPREAD: '구간 분산',
};

/**
 * @param {string} code
 * @returns {string}
 */
export function explanationLabel(code) {
    return EXPLANATION_LABELS[code] ?? code;
}

const MAX_LOCKED = 5;
const MIN_REMAINING_AFTER_EXCLUSION = 6;

/**
 * 번호 추천 화면의 상태와 생성 흐름을 담는다.
 *
 * - 화면 기본값은 balanced·5개다(02문서 1절) — API 자체의 생략 기본값(random·1개)과 다르므로
 *   항상 두 필드를 명시적으로 전송한다.
 * - 한 번호는 고정·제외·미선택 중 하나만 가진다(반대 모드에서 누르면 이동한다).
 * - 응답 순서 번호로 늦게 도착한 이전 요청의 결과를 무시하고, 생성 당시 조건을 저장해 두어
 *   화면 조건이 바뀌면 "낡은 결과"임을 알린다.
 */
export function useRecommendation() {
    const strategy = ref('balanced');
    const count = ref(5);
    const lockedNumbers = reactive(new Set());
    const excludedNumbers = reactive(new Set());
    const selectionMode = ref('locked'); // 'locked' | 'excluded'

    const status = ref('idle'); // idle | generating | ready | history-not-ready | error
    /** @type {import('vue').Ref<RecommendationResponse|null>} */
    const result = ref(null);
    /** @type {import('vue').Ref<string|null>} */
    const errorMessage = ref(null);
    /** @type {import('vue').Ref<RecommendationConditions|null>} */
    const lastConditions = ref(null);
    const liveAnnouncement = ref('');

    let requestSeq = 0;

    const lockedList = computed(() => [...lockedNumbers].sort((a, b) => a - b));
    const excludedList = computed(() => [...excludedNumbers].sort((a, b) => a - b));

    const clientValidationError = computed(() => {
        if (!Number.isInteger(count.value) || count.value < 1 || count.value > 10) {
            return '생성 개수는 1~10 사이의 정수여야 합니다.';
        }
        if (lockedNumbers.size > MAX_LOCKED) {
            return `고정 번호는 최대 ${MAX_LOCKED}개까지 선택할 수 있습니다.`;
        }
        if (45 - excludedNumbers.size < MIN_REMAINING_AFTER_EXCLUSION) {
            return '제외 후 남는 번호가 6개 미만입니다. 제외 번호를 줄여 주세요.';
        }
        return null;
    });

    const canSubmit = computed(() => status.value !== 'generating' && clientValidationError.value === null);

    /** @returns {RecommendationConditions} */
    function currentConditions() {
        return {
            strategy: strategy.value,
            count: count.value,
            locked: lockedList.value,
            excluded: excludedList.value,
        };
    }

    const isStale = computed(() => {
        // status가 아니라 "표시 중인 result가 현재 조건과 다른가"만 본다 — 조건을 바꾸고
        // 재시도했다가 generating/error/history-not-ready로 빠져도 이전 result는 화면에 남아
        // 있으므로, 이 상태들에서도 조건 변경 안내를 계속 보여줘야 한다(개선 보고서 F02).
        if (!lastConditions.value || !result.value) {
            return false;
        }
        const current = currentConditions();
        const last = lastConditions.value;
        return (
            current.strategy !== last.strategy
            || current.count !== last.count
            || current.locked.join(',') !== last.locked.join(',')
            || current.excluded.join(',') !== last.excluded.join(',')
        );
    });

    /**
     * 한 번호는 고정·제외·미선택 중 하나만 가진다. 현재 모드에서 다시 누르면 선택을 해제한다.
     * @param {number} n
     */
    function toggleNumber(n) {
        if (selectionMode.value === 'locked') {
            if (lockedNumbers.has(n)) {
                lockedNumbers.delete(n);
            } else {
                excludedNumbers.delete(n);
                lockedNumbers.add(n);
            }
        } else {
            if (excludedNumbers.has(n)) {
                excludedNumbers.delete(n);
            } else {
                lockedNumbers.delete(n);
                excludedNumbers.add(n);
            }
        }
    }

    /** @param {number} n */
    function stateOf(n) {
        if (lockedNumbers.has(n)) return 'locked';
        if (excludedNumbers.has(n)) return 'excluded';
        return 'unselected';
    }

    /**
     * @param {unknown} error
     * @returns {string}
     */
    function errorMessageFor(error) {
        // ProblemDetail의 detail은 이미 한국어 사용자 문구다(ApiExceptionHandler). 본문이 없는
        // 403(CSRF·세션 만료)은 http.js가 고정 안내 문구를 채워 준다.
        return messageOf(error);
    }

    async function generate() {
        if (!canSubmit.value) {
            return;
        }

        const seq = ++requestSeq;
        status.value = 'generating';
        errorMessage.value = null;
        liveAnnouncement.value = '추천 번호를 생성하는 중입니다.';

        const conditions = currentConditions();

        try {
            /** @type {RecommendationResponse} */
            const response = await api.post(API.NUMBERS_RECOMMEND, {
                strategy: conditions.strategy,
                count: conditions.count,
                lockedNumbers: conditions.locked,
                excludedNumbers: conditions.excluded,
            });

            if (seq !== requestSeq) {
                return; // 더 최근 요청이 이미 진행 중이다 — 이 응답은 버린다.
            }

            result.value = response;
            lastConditions.value = conditions;
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

            status.value = 'error';
            errorMessage.value = errorMessageFor(error);
            liveAnnouncement.value = errorMessage.value;
        }
    }

    return {
        strategy,
        count,
        selectionMode,
        status,
        result,
        errorMessage,
        liveAnnouncement,
        lockedList,
        excludedList,
        clientValidationError,
        canSubmit,
        isStale,
        toggleNumber,
        stateOf,
        generate,
    };
}
