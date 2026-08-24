import { vi } from "vitest";

/**
 * API 어댑터 테스트 공용 fetch mock 헬퍼
 *
 * `ops/api.test.ts`·`transport.test.ts` 등 여섯 곳이 이 헬퍼를 거의 동일하게 각자
 * 인라인으로 정의해 왔다(QA-FE-01). 그 여섯 곳은 이미 안정적으로 동작 중이라 이번
 * 변경 범위 밖으로 남기고, 새로 추가하는 어댑터 테스트들만 이 공용 버전을 쓴다.
 */

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

/** 204 No Content 등 본문이 없는 성공 응답. */
export function emptyResponse(status = 204): Response {
  return new Response(null, { status });
}

export function mockFetch(response: Response | Promise<Response>) {
  const spy = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", spy);
  return spy;
}

export function headersOf(spy: ReturnType<typeof vi.fn>): Record<string, string> {
  const init = spy.mock.calls[0]?.[1] as RequestInit | undefined;
  return (init?.headers ?? {}) as Record<string, string>;
}

export function urlOf(spy: ReturnType<typeof vi.fn>, callIndex = 0): string {
  return spy.mock.calls[callIndex]?.[0] as string;
}

export function initOf(spy: ReturnType<typeof vi.fn>, callIndex = 0): RequestInit {
  return (spy.mock.calls[callIndex]?.[1] ?? {}) as RequestInit;
}
