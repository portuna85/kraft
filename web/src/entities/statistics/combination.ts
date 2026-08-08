/**
 * 조합 입력 규칙 — improvement_fe.md §23.6 불변식
 *
 * **schema.ts와 분리해 둔 이유는 번들이다.** 이 함수들은 번호판(클라이언트 컴포넌트)이
 * 쓰는데, schema.ts에 두면 그 import가 valibot과 통계 스키마 전량을 클라이언트 번들로
 * 끌고 들어온다. 실제로 /analysis가 그 때문에 예산을 17KB 넘겼다. 런타임 검증이 필요
 * 없는 순수 규칙은 스키마 모듈 밖에 둔다.
 */

/** 조합 하나의 번호 개수. 백엔드 @LottoNumbers 제약과 같아야 한다. */
export const COMBINATION_SIZE = 6;

/** 로또 번호 범위. 백엔드 @LottoNumbers와 같아야 한다. */
export const MIN_LOTTO_NUMBER = 1;
export const MAX_LOTTO_NUMBER = 45;

/**
 * 분석 요청으로 보낼 수 있는 조합인지.
 *
 * 백엔드도 @LottoNumbers로 같은 것을 검사하지만, 화면이 먼저 걸러야 사용자가 400을
 * 보는 대신 "2개 더 고르세요"를 본다.
 */
export function isCompleteCombination(numbers: readonly number[]): boolean {
  if (numbers.length !== COMBINATION_SIZE) return false;
  if (new Set(numbers).size !== numbers.length) return false;
  return numbers.every(isLottoNumber);
}

function isLottoNumber(value: number): boolean {
  return Number.isInteger(value) && value >= MIN_LOTTO_NUMBER && value <= MAX_LOTTO_NUMBER;
}

/**
 * 사람이 친 문자열에서 번호를 뽑는다 — "1, 2, 3" · "1 2 3" · "1,2,3," 모두 받는다.
 *
 * 범위를 벗어나거나 숫자가 아닌 조각은 버린다. 여기서 걸러 두면 URL을 손으로 고친
 * 요청(`?numbers=abc`·`?numbers=99`)이 백엔드 400이 아니라 화면의 안내로 끝난다.
 * 중복은 버리지 않고 남긴다 — 중복 자체가 사용자에게 알려야 할 입력 오류다.
 */
export function parseCombinationInput(raw: string): number[] {
  return raw
    .split(/[\s,]+/)
    .filter((piece) => piece.length > 0)
    .map(Number)
    .filter(isLottoNumber)
    .slice(0, COMBINATION_SIZE);
}
