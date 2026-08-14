/**
 * 요청 타임아웃 (레거시 W-1에서 승계한 제약)
 *
 * 전 요청에 5초 상한을 건다. 상한이 없으면 SSR이 백엔드 지연에 그대로 묶여
 * 페이지 전체가 무한 대기한다. 호출부 signal이 있으면 둘을 합성해, 컴포넌트
 * 언마운트 abort와 타임아웃 중 먼저 오는 쪽이 이기게 한다.
 */

export const REQUEST_TIMEOUT_MS = 5_000;

export function composeAbortSignal(
  external: AbortSignal | undefined,
  timeoutMs: number = REQUEST_TIMEOUT_MS,
): AbortSignal {
  const timeout = AbortSignal.timeout(timeoutMs);
  return external ? AbortSignal.any([external, timeout]) : timeout;
}
