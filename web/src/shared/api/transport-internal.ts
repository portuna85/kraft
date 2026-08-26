/**
 * transport.ts와 ops-transport.ts가 공유하는 응답 파싱/에러 변환 로직(TD-009).
 *
 * 요청 생성부(인증·캐시·CSRF·타임아웃 정책)는 두 파일에 각자 남긴다 — 여기엔 응답을
 * 받은 뒤 공통으로 하는 일(JSON 파싱, 실패 응답 분류, 스키마 검증)만 둔다.
 */

import * as v from "valibot";

import { ApiError, readBackendError } from "./error";

export type Schema<T> = v.BaseSchema<unknown, T, v.BaseIssue<unknown>>;

export async function parseJson(response: Response): Promise<unknown> {
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

export function failureFrom(response: Response, body: unknown): ApiError {
  const { code, message, requestId } = readBackendError(body);
  const kind = response.status >= 500 ? "server" : "client";
  // 5xx 원문 메시지는 사용자에게 그대로 보이면 안 된다(§20.6) — 코드·상태는 보존하되
  // 화면 문구는 호출부가 kind/status로 고른다.
  return new ApiError(kind, message ?? `요청이 실패했습니다 (${response.status}).`, {
    code,
    status: response.status,
    // 본문에 없으면 응답 헤더(RequestIdFilter가 전 응답에 붙인다)로 보완한다.
    requestId: requestId ?? response.headers.get("X-Request-Id"),
  });
}

export function validate<T>(schema: Schema<T>, body: unknown, url: string): T {
  const result = v.safeParse(schema, body);
  if (result.success) return result.output;

  // 계약 위반은 서버 쪽 문제로 분류한다 — 사용자가 재시도해도 달라지지 않는다.
  throw new ApiError("server", `응답이 계약과 다릅니다: ${url}`, {
    code: "SCHEMA_MISMATCH",
    cause: result.issues,
  });
}
