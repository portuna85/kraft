// @ts-check

/**
 * 번호 추천 요청·표시에 쓰는 순수 로직. DOM과 HTTP에 의존하지 않아 `node --test`로 검사한다
 * (`useRecommendation.js`는 `@core/http.js`를 거쳐 모듈 로드 시점에 `document`를 읽는다).
 *
 * API(`POST /api/v1/numbers/recommend`)는 개수만 받는다. 역대 1등 조합 제외는 서버가 항상 적용하므로 화면에서 고르는 값이 아니다.
 */

export const DEFAULT_COUNT = 5;
/** API의 count 범위(1~10). */
export const COUNT_OPTIONS = Object.freeze(Array.from({ length: 10 }, (_, index) => index + 1));

export const HISTORY_NOT_READY_CODE = 'RECOMMENDATION_HISTORY_NOT_READY';
export const GENERATION_LIMIT_CODE = 'RECOMMENDATION_GENERATION_LIMIT_REACHED';

/**
 * @param {{ count: number }} choice
 * @returns {{ count: number }}
 */
export function buildRequest({ count }) {
    return { count };
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
        return `${message} 개수를 줄여 보세요.`;
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
