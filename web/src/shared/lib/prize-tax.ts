/**
 * 복권 당첨금 세후 실수령액 계산
 *
 * 소득세법 제129조, 복권 및 복권기금법 시행령 제8조의2 기준 —
 * `web/src/app/(public)/info/[slug]/content.tsx`의 "세후 당첨금 계산" 문구와
 * 같은 세율을 코드화한다: 200만원 이하는 비과세, 200만원 초과~3억원 이하 구간은
 * 22%(기타소득세 20% + 지방소득세 2%), 3억원 초과 구간은 33%(기타소득세 30% +
 * 지방소득세 3%)를 구간별로 누진 적용한다.
 *
 * 원천징수세액은 원 단위 절사가 일반적이므로 반올림하지 않고 내림한다.
 */
const TAX_FREE_THRESHOLD = 2_000_000;
const HIGH_BRACKET_THRESHOLD = 300_000_000;
const LOW_RATE = 0.22;
const HIGH_RATE = 0.33;

export function calculateNetPrize(amount: number): number {
  if (amount <= TAX_FREE_THRESHOLD) return amount;

  const lowBracketTaxable = Math.min(amount, HIGH_BRACKET_THRESHOLD) - TAX_FREE_THRESHOLD;
  const highBracketTaxable = Math.max(amount - HIGH_BRACKET_THRESHOLD, 0);

  const tax = Math.floor(lowBracketTaxable * LOW_RATE + highBracketTaxable * HIGH_RATE);

  return amount - tax;
}
