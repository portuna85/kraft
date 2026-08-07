/**
 * CSRF double-submit — improvement_fe.md §13.4, §20.4
 *
 * 백엔드가 내려준 XSRF-TOKEN 쿠키를 읽어 X-XSRF-TOKEN 헤더로 되돌려 보낸다.
 * 이 헤더가 빠지면 쓰기 요청이 전부 403이 된다(R-5) — transport가 쓰기 메서드에서
 * 이 헤더를 타입 차원에서 요구하므로 호출부가 빼먹을 수 없다.
 */

export const CSRF_COOKIE_NAME = "XSRF-TOKEN";
export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

/** 브라우저 전용. 쿠키가 없으면 null — 임의 값으로 채우면 403 원인을 감춘다. */
export function readCsrfToken(): string | null {
  if (typeof document === "undefined") return null;

  const prefix = `${CSRF_COOKIE_NAME}=`;
  for (const part of document.cookie.split(";")) {
    const entry = part.trim();
    if (entry.startsWith(prefix)) {
      return decodeURIComponent(entry.slice(prefix.length));
    }
  }
  return null;
}
