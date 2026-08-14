/**
 * 운영 콘솔 전송 계층 — improvement_fe.md §23.14, §25.7
 *
 * `transport.ts`의 `browserQuery`/`browserMutate`를 그대로 못 쓰는 이유는 인증
 * 방식이 다르기 때문이다 — 커뮤니티 등 일반 쓰기는 쿠키 기반 CSRF 헤더를 쓰지만,
 * `/ops-api/*`는 백엔드 `OpsTokenFilter`가 `X-Ops-Token` 헤더를 요구한다(불일치
 * 401, 미설정 503). `browserMutate` 안에 조건분기를 넣으면 "쓰기는 항상 CSRF"라는
 * 그 함수의 불변식이 흐려진다 — 별도 함수로 분리한다.
 *
 * 토큰은 서버에 저장하지 않는다. 운영자가 세션마다 화면에 직접 입력하고, 그 값을
 * 호출부(컴포넌트 상태)가 매 요청에 실어 보낸다.
 */

import { toApiError } from "./error";
import { composeAbortSignal } from "./timeout";
import { parseJson, failureFrom, validate, type Schema } from "./transport-internal";

const OPS_TOKEN_HEADER_NAME = "X-Ops-Token";

async function opsRequest<T>(url: string, schema: Schema<T>, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch (cause) {
    // TD-009: 예전엔 abort/timeout까지 항상 kind:"network"로 잘못 분류됐다. toApiError는
    // DOMException AbortError를 kind:"timeout"으로 올바르게 구분한다(transport.ts와 동일 패턴) —
    // TD-001에서 늘린 20초 수집 타임아웃이 실제로 걸렸을 때도 정확히 분류되게 한다.
    throw toApiError(cause, "운영 서버에 연결할 수 없습니다.");
  }

  const body = await parseJson(response);
  if (!response.ok) throw failureFrom(response, body);
  return validate(schema, body, url);
}

/** 운영 콘솔 조회. 캐시하면 방금 트리거한 수집 결과가 안 보일 수 있어 항상 no-store다. */
export function opsQuery<T>(path: string, schema: Schema<T>, token: string): Promise<T> {
  return opsRequest(path, schema, {
    method: "GET",
    cache: "no-store",
    headers: { accept: "application/json", [OPS_TOKEN_HEADER_NAME]: token },
    signal: composeAbortSignal(undefined),
  });
}

export function opsMutate<T>(
  path: string,
  schema: Schema<T>,
  options: {
    token: string;
    method: "POST" | "PUT" | "PATCH" | "DELETE";
    body?: unknown;
    // TD-001: 수동 수집(/ops/collect/*)은 백엔드 재시도 봉투(최악 약 16.5초)가 기본
    // 5초 상한보다 길어 클라이언트가 먼저 타임아웃되면서 실제로는 성공할 수집을
    // 실패로 오인시킨다. 다른 ops 호출(summary/logs/rounds)은 기본값을 그대로 쓰고,
    // 수집 두 호출만 entities/ops/api.ts에서 이 값을 넘겨 확장한다.
    timeoutMs?: number;
  },
): Promise<T> {
  const hasBody = options.body !== undefined;
  return opsRequest(path, schema, {
    method: options.method,
    cache: "no-store",
    headers: {
      accept: "application/json",
      [OPS_TOKEN_HEADER_NAME]: options.token,
      ...(hasBody ? { "content-type": "application/json" } : {}),
    },
    ...(hasBody ? { body: JSON.stringify(options.body) } : {}),
    signal: composeAbortSignal(undefined, options.timeoutMs),
  });
}
