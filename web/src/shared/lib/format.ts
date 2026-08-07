/**
 * 표시 포맷 — improvement_fe.md §18.3
 *
 * 서버와 클라이언트가 **같은 결과**를 내야 한다. `toLocaleString()`을 로캘 지정 없이
 * 쓰면 서버(컨테이너 기본 로캘)와 브라우저가 다른 문자열을 만들어 하이드레이션이
 * 어긋난다 — 그래서 로캘과 타임존을 항상 명시한다.
 */

const KRW = new Intl.NumberFormat("ko-KR");

export function formatNumber(value: number): string {
  return KRW.format(value);
}

/**
 * 당첨금은 억/만 단위로 줄여 읽기 쉽게 한다. 원 단위 전체 숫자는 자릿수를 세어야
 * 크기를 알 수 있어, 정작 "얼마인지"가 늦게 전달된다.
 */
export function formatPrize(amount: number): string {
  if (amount >= 100_000_000) {
    const billions = amount / 100_000_000;
    const rounded = Math.round(billions * 10) / 10;
    return `${formatNumber(rounded)}억 원`;
  }
  if (amount >= 10_000) {
    return `${formatNumber(Math.round(amount / 10_000))}만 원`;
  }
  return `${formatNumber(amount)}원`;
}

/** YYYY-MM-DD → "2026년 8월 1일". 입력이 예상 형태가 아니면 그대로 돌려준다. */
export function formatDrawDate(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (match === null) return isoDate;

  const [, year, month, day] = match;
  return `${year}년 ${Number(month)}월 ${Number(day)}일`;
}
