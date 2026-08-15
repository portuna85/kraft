/**
 * 추첨 시각
 *
 * 로또 6/45는 매주 토요일 20시 35분(KST)에 추첨한다. 이 시각은 **사용자 브라우저의
 * 시간대와 무관하게 고정**이므로 UTC로 계산한다 — 로컬 요일·시각으로 계산하면 한국
 * 밖에서 보는 사용자에게 하루가 어긋난다. KST(UTC+9) 토요일 20:35 = UTC 토요일 11:35.
 */
const DRAW_WEEKDAY_UTC = 6; // 토요일
const DRAW_HOUR_UTC = 11;
const DRAW_MINUTE_UTC = 35;

/** `now` 이후에 처음 오는 추첨 시각. 추첨 시각 정각이면 다음 주로 넘긴다. */
export function nextDrawTime(now: Date): Date {
  const daysUntilDraw = (DRAW_WEEKDAY_UTC - now.getUTCDay() + 7) % 7;

  const candidate = new Date(
    Date.UTC(
      now.getUTCFullYear(),
      now.getUTCMonth(),
      now.getUTCDate() + daysUntilDraw,
      DRAW_HOUR_UTC,
      DRAW_MINUTE_UTC,
      0,
      0,
    ),
  );

  if (candidate.getTime() <= now.getTime()) {
    candidate.setUTCDate(candidate.getUTCDate() + 7);
  }
  return candidate;
}

export type Countdown = { days: number; hours: number; minutes: number; seconds: number };

/** 남은 시간을 일·시·분·초로 쪼갠다. 이미 지난 시각이면 전부 0이다. */
export function countdownUntil(target: Date, now: Date): Countdown {
  const remainingMs = Math.max(target.getTime() - now.getTime(), 0);
  const totalSeconds = Math.floor(remainingMs / 1000);

  return {
    days: Math.floor(totalSeconds / 86400),
    hours: Math.floor((totalSeconds % 86400) / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  };
}

/** "3일 14시간 22분 10초" — 남은 일수가 0이면 일 단위를 생략한다. */
export function formatCountdown({ days, hours, minutes, seconds }: Countdown): string {
  const parts = days > 0 ? [`${days}일`] : [];
  parts.push(`${hours}시간`, `${minutes}분`, `${seconds}초`);
  return parts.join(" ");
}
