/**
 * 로그인 왕복 후 원위치 복귀 — improvement_fe.md §25.1
 *
 * web-legacy(src/lib/return-to.ts)와 같은 계약을 그대로 재현한다. 백엔드
 * OAuth 콜백은 항상 공개 기본 URL로 돌아오므로(서버 리다이렉트 대상은 안
 * 바꾼다), 로그인 진입 직전 있던 경로를 sessionStorage에 저장해 뒀다가
 * 돌아온 뒤 클라이언트에서 한 번만 이동시킨다.
 *
 * 저장·소비 모두 `/`로 시작하고 `//`로 시작하지 않는 값만 허용한다 —
 * `//evil.com` 같은 프로토콜 상대 경로를 그대로 신뢰하면 오픈 리다이렉트가
 * 된다.
 */
const RETURN_TO_KEY = "kraft-return-to";

function isSafeInternalPath(value: string): boolean {
  return value.startsWith("/") && !value.startsWith("//");
}

export function saveReturnTo(pathname: string): void {
  if (!isSafeInternalPath(pathname)) return;
  window.sessionStorage.setItem(RETURN_TO_KEY, pathname);
}

export function consumeReturnTo(): string | null {
  const value = window.sessionStorage.getItem(RETURN_TO_KEY);
  window.sessionStorage.removeItem(RETURN_TO_KEY);
  if (value === null || !isSafeInternalPath(value)) return null;
  return value;
}
