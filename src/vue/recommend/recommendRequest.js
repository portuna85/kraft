// @ts-check

/**
 * 번호 추천 요청·표시에 쓰는 순수 로직. DOM과 HTTP에 의존하지 않아 `node --test`로 검사한다
 * (`useRecommendation.js`는 `@core/http.js`를 거쳐 모듈 로드 시점에 `document`를 읽는다).
 *
 * API(`POST /api/v1/numbers/recommend`)는 전략·개수·고정·제외 번호를 모두 받는다. 과거 1등 조합 제외는 서버가
 * 모든 전략에 항상 적용하므로 화면에서 고르는 값이 아니다.
 */

/**
 * 화면에 보이는 추천 방식. 문구는 홈(`home.html`)의 방식 설명과 같다. `value`는 API의 전략 이름이다.
 * 첫 항목이 기본값이다 — 예전 화면이 고정으로 보내던 전략을 그대로 기본으로 둬 동작이 바뀌지 않는다.
 */
export const STRATEGIES = Object.freeze([
    { value: 'reduce_shared_winner_risk', title: '공동 당첨 위험 완화', description: '사람들이 자주 고르는 패턴을 피합니다.' },
    { value: 'balanced', title: '균형 조합', description: '홀짝·고저·합계·구간 분포를 고려합니다.' },
    { value: 'random', title: '무작위', description: '조건 없이 조합합니다.' },
]);

export const DEFAULT_STRATEGY = STRATEGIES[0].value;
export const DEFAULT_COUNT = 5;
/** API의 count 범위(1~10). */
export const COUNT_OPTIONS = Object.freeze(Array.from({ length: 10 }, (_, index) => index + 1));

export const HISTORY_NOT_READY_CODE = 'RECOMMENDATION_HISTORY_NOT_READY';
export const GENERATION_LIMIT_CODE = 'RECOMMENDATION_GENERATION_LIMIT_REACHED';

/**
 * @param {{ strategy: string, count: number }} choice
 * @returns {{ strategy: string, count: number, lockedNumbers: number[], excludedNumbers: number[] }}
 */
export function buildRequest({ strategy, count }) {
    return { strategy, count, lockedNumbers: [], excludedNumbers: [] };
}

/**
 * 서버가 준 사용자 문구(ProblemDetail.detail)에, 사용자가 조치할 수 있는 오류에는 다음 행동을 덧붙인다.
 *
 * @param {string | undefined} code ProblemDetail의 code 확장 속성
 * @param {string} message 이미 한국어인 서버 문구
 * @returns {string}
 */
export function describeFailure(code, message) {
    if (code === GENERATION_LIMIT_CODE) {
        return `${message} 개수를 줄이거나 조건을 완화해 보세요.`;
    }
    return message;
}

/**
 * 결과 행의 라벨(A, B, C…). count 상한이 10이라 J까지면 충분하다.
 *
 * @param {number} index 0부터
 * @returns {string}
 */
export function letterOf(index) {
    return String.fromCharCode(65 + index);
}

/**
 * @param {number[]} numbers
 * @returns {string} 예: `3, 12, 19, 28, 34, 43`
 */
export function formatCombination(numbers) {
    return numbers.join(', ');
}

/**
 * 전체 복사 형식: 한 줄에 한 조합, 라벨 없이.
 *
 * @param {{ numbers: number[] }[]} items
 * @returns {string}
 */
export function formatAll(items) {
    return items.map((item) => formatCombination(item.numbers)).join('\n');
}
