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

import * as v from "valibot";

import { ApiError, readBackendError } from "./error";
import { composeAbortSignal } from "./timeout";

type Schema<T> = v.BaseSchema<unknown, T, v.BaseIssue<unknown>>;

const OPS_TOKEN_HEADER_NAME = "X-Ops-Token";

async function parseJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text.length === 0) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch (cause) {
    throw new ApiError("server", "서버 응답을 해석할 수 없습니다.", {
      status: response.status,
      cause,
    });
  }
}

function failureFrom(response: Response, body: unknown): ApiError {
  const { code, message } = readBackendError(body);
  const kind = response.status >= 500 ? "server" : "client";
  return new ApiError(kind, message ?? `요청이 실패했습니다 (${response.status}).`, {
    code,
    status: response.status,
  });
}

function validate<T>(schema: Schema<T>, body: unknown, url: string): T {
  const result = v.safeParse(schema, body);
  if (result.success) return result.output;
  throw new ApiError("server", `응답이 계약과 다릅니다: ${url}`, {
    code: "SCHEMA_MISMATCH",
    cause: result.issues,
  });
}

async function opsRequest<T>(url: string, schema: Schema<T>, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch (cause) {
    throw new ApiError("network", "운영 서버에 연결할 수 없습니다.", { cause });
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
