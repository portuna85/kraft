/**
 * error.tsx 경계에서 서버 로그로 오류를 올린다
 *
 * 세그먼트 `error.tsx`는 클라이언트 컴포넌트라 서버 사이드 로그에 자동으로 남지
 * 않는다. `/api/client-error`가 받아 서버 콘솔에 남긴다. 이 호출 자체가 실패해도
 * 사용자에게 보이는 오류 화면에는 영향을 주지 않는다 — 관측 경로일 뿐이다.
 */
export function reportClientError(error: Error & { digest?: string }, route: string): void {
  const body = JSON.stringify({ message: error.message, digest: error.digest, route });

  // sendBeacon은 페이지 이탈 중에도 전송을 보장하고, 실패해도 던지지 않는다.
  if (typeof navigator.sendBeacon === "function") {
    navigator.sendBeacon("/api/client-error", body);
    return;
  }

  void fetch("/api/client-error", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
    keepalive: true,
  }).catch(() => undefined);
}
