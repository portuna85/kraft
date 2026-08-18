/**
 * 표시 포맷
 *
 * 서버와 클라이언트가 **같은 결과**를 내야 한다. `toLocaleString()`을 로캘 지정 없이
 * 쓰면 서버(컨테이너 기본 로캘)와 브라우저가 다른 문자열을 만들어 하이드레이션이
 * 어긋난다 — 그래서 로캘과 타임존을 항상 명시한다.
 */

const KRW = new Intl.NumberFormat("ko-KR");

export function formatNumber(value: number): string {
  return KRW.format(value);
}

/** 원 단위 그대로, 쉼표로만 구분한다("#,###,### 원"). 자릿수를 빠르게 대조해야 하는
 * 실수령액처럼 억/만 축약(`formatPrize`)이 오히려 비교를 어렵게 하는 곳에 쓴다. */
export function formatWon(amount: number): string {
  return `${formatNumber(amount)}원`;
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

/**
 * ISO 시각 → "2026년 8월 1일 21:00". 타임존을 고정한다.
 *
 * 백엔드는 OffsetDateTime을 보내고 서버 컨테이너는 UTC로 돈다. 타임존을 지정하지 않으면
 * 서버는 UTC로, 브라우저는 사용자 로컬로 렌더해 하이드레이션이 어긋난다. 이 서비스의
 * 회차·추첨 시각은 전부 한국 기준이므로 Asia/Seoul로 고정하는 것이 의미상으로도 맞다.
 */
const SEOUL_DATE_TIME = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "long",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

export function formatDateTime(isoInstant: string): string {
  const parsed = new Date(isoInstant);
  // 파싱에 실패한 값을 "Invalid Date"로 내보내면 화면에 그 문자열이 그대로 보인다.
  if (Number.isNaN(parsed.getTime())) return isoInstant;
  return SEOUL_DATE_TIME.format(parsed);
}

/** YYYY-MM-DD → "2026년 8월 1일". 입력이 예상 형태가 아니면 그대로 돌려준다. */
export function formatDrawDate(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (match === null) return isoDate;

  const [, year, month, day] = match;
  return `${year}년 ${Number(month)}월 ${Number(day)}일`;
}
