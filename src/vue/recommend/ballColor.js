// @ts-check

/**
 * 번호 구간별 공 색. 서버(`LottoBallColor.java`)와 같은 경계를 쓴다 — 두 곳에 있어야 하는 이유는 서버가 첫 화면의
 * 최신 회차 공을 직접 렌더링하고, 이 화면은 생성된 조합을 브라우저에서 그리기 때문이다. 한쪽만 고치면
 * `LottoBallColorJsSyncTest`가 즉시 깨진다.
 *
 * 경계는 아래 모양을 그대로 유지한다 — 그 테스트가 `upTo: N, name: '...'` 쌍과 `BALL_COLOR_LAST`를 정규식으로 읽는다.
 */
export const BALL_COLOR_BANDS = [
    { upTo: 10, name: 'yellow' },
    { upTo: 20, name: 'blue' },
    { upTo: 30, name: 'red' },
    { upTo: 40, name: 'gray' },
];

/** 마지막 구간(41~45)의 색 이름. */
export const BALL_COLOR_LAST = 'green';

/**
 * @param {number} n 1~45
 * @returns {string}
 */
export function ballColorName(n) {
    const band = BALL_COLOR_BANDS.find((candidate) => n <= candidate.upTo);
    return band ? band.name : BALL_COLOR_LAST;
}

/**
 * @param {number} n 1~45
 * @returns {string} 공용 공 스타일의 색 변형 클래스
 */
export function ballColorClass(n) {
    return `lotto-ball--${ballColorName(n)}`;
}
