/**
 * 네트워크 전송 계층 — improvement_fe.md §13.1~§13.5
 *
 * 설계 규칙 세 가지:
 *
 * 1. **스키마는 선택 인자가 아니다.** 모든 함수가 Valibot 스키마를 필수로 받는다.
 *    현행은 손으로 쓴 얕은 타입가드가 최상위 필드 존재만 확인해, 계약이 어긋나면
 *    undefined가 화면 깊숙이 전파된 뒤 알 수 없는 위치에서 터졌다(§6.2 H-3).
 * 2. **쓰기는 CSRF 없이 아예 불가능하다.** 읽기(`browserQuery`)와 쓰기(`browserMutate`)를
 *    다른 함수로 갈라 두고, 쓰기는 transport가 직접 헤더를 붙인다(R-5).
 * 3. **자동 재시도 없음.** 재시도는 호출부가 사용자 의도로 판단할 일이다(§13.7).
 *
 * 이 모듈은 전송만 안다 — 캐시 태그와 재검증 주기는 각 entity의 api.ts가 소유한다(§7.5).
 */

import * as v from "valibot";

import { CSRF_HEADER_NAME, readCsrfToken } from "./csrf";
import { ApiError, toApiError } from "./error";
import { DEVICE_TOKEN_HEADER_NAME, ensureDeviceToken } from "./device-token";
import { composeAbortSignal } from "./timeout";
import { parseJson, failureFrom, validate, type Schema } from "./transport-internal";

/**
 * 204 No Content 응답용 스키마.
 *
 * 본문이 없는 응답도 스키마를 거치게 한다 — 예외를 두면 "이 호출은 스키마가 필요 없다"는
 * 판단이 호출부마다 생기고, 그 틈으로 검증 없는 경로가 다시 늘어난다. parseJson이 빈
 * 본문을 null로 돌려주므로 null을 기대하는 것이 곧 "본문이 없어야 한다"는 단언이 된다.
 */
export const noContentSchema = v.null();

type CacheStrategy =
  /** 태그 무효화 대상. `/api/revalidate` 웹훅이 이 태그를 지운다(§13.6) */
  | { mode: "revalidate"; seconds: number; tags: readonly string[] }
  /** 캐시 금지. 조회수 부수효과가 있는 GET 등(레거시 M-4) */
  | { mode: "no-store" };

/* ────────────────────────────────────────────────────────────────────────
 * 서버(RSC·Route Handler) 전용
 * ──────────────────────────────────────────────────────────────────────── */

export async function serverFetch<T>(
  url: string,
  schema: Schema<T>,
  options: {
    cache: CacheStrategy;
    headers?: HeadersInit;
    signal?: AbortSignal;
    /**
     * 부수효과 없는 조회인데 백엔드가 POST로 받는 경우에만 쓴다(예: 번호 6개를 실어야
     * 하는 조합 분석). Next의 fetch 캐시는 GET에만 걸리므로 이때 cache는 no-store여야
     * 하고, 그 조합이 아니면 캐시가 조용히 무시돼 "캐시된 줄 알았는데 아니었다"가 된다.
     *
     * 상태를 바꾸는 요청에는 쓰지 않는다 — 그런 요청은 CSRF 처리가 필요하고, 그것은
     * 브라우저 쪽 browserMutate의 일이다.
     */
    method?: "POST";
    body?: unknown;
  },
): Promise<T> {
  const { cache } = options;
  const hasBody = options.body !== undefined;

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? "GET",
      headers: {
        accept: "application/json",
        ...(hasBody ? { "content-type": "application/json" } : {}),
        ...options.headers,
      },
      ...(hasBody ? { body: JSON.stringify(options.body) } : {}),
      signal: composeAbortSignal(options.signal),
      ...(cache.mode === "no-store"
        ? { cache: "no-store" as const }
        : { next: { revalidate: cache.seconds, tags: [...cache.tags] } }),
    });
  } catch (cause) {
    throw toApiError(cause, "백엔드에 연결할 수 없습니다.");
  }

  const body = await parseJson(response);
  if (!response.ok) throw failureFrom(response, body);
  return validate(schema, body, url);
}

/* ────────────────────────────────────────────────────────────────────────
 * 브라우저 전용
 * ──────────────────────────────────────────────────────────────────────── */

type BrowserOptions = {
  /** 익명 스코프 요청에만 붙인다 — 로그인 사용자 요청에 붙이면 스코프가 섞인다(§25.8) */
  deviceScoped?: boolean;
  signal?: AbortSignal;
};

function browserHeaders(options: BrowserOptions, extra?: Record<string, string>): HeadersInit {
  const headers: Record<string, string> = { accept: "application/json", ...extra };
  if (options.deviceScoped) {
    const token = ensureDeviceToken();
    if (token) headers[DEVICE_TOKEN_HEADER_NAME] = token;
  }
  return headers;
}

async function browserRequest<T>(url: string, schema: Schema<T>, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch (cause) {
    throw toApiError(cause, "요청을 보낼 수 없습니다. 연결을 확인해 주세요.");
  }

  const body = await parseJson(response);
  if (!response.ok) throw failureFrom(response, body);
  return validate(schema, body, url);
}

/** 브라우저 조회. 사용자별 데이터는 공유 캐시에 담기면 안 되므로 항상 no-store다. */
export function browserQuery<T>(
  url: string,
  schema: Schema<T>,
  options: BrowserOptions = {},
): Promise<T> {
  return browserRequest(url, schema, {
    method: "GET",
    cache: "no-store",
    credentials: "same-origin",
    headers: browserHeaders(options),
    signal: composeAbortSignal(options.signal),
  });
}

/**
 * 브라우저 쓰기. CSRF 헤더를 transport가 직접 붙이므로 호출부가 빼먹을 수 없다.
 * 쿠키가 없으면 요청을 보내지 않고 403 대신 명확한 오류를 던진다.
 */
export function browserMutate<T>(
  url: string,
  schema: Schema<T>,
  options: BrowserOptions & { method: "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown },
): Promise<T> {
  const csrf = readCsrfToken();
  if (csrf === null) {
    return Promise.reject(
      new ApiError(
        "client",
        "보안 토큰이 없어 요청을 보낼 수 없습니다. 페이지를 새로 고친 뒤 다시 시도해 주세요.",
        {
          code: "CSRF_TOKEN_MISSING",
        },
      ),
    );
  }

  const hasBody = options.body !== undefined;
  return browserRequest(url, schema, {
    method: options.method,
    cache: "no-store",
    credentials: "same-origin",
    headers: browserHeaders(options, {
      [CSRF_HEADER_NAME]: csrf,
      ...(hasBody ? { "content-type": "application/json" } : {}),
    }),
    ...(hasBody ? { body: JSON.stringify(options.body) } : {}),
    signal: composeAbortSignal(options.signal),
  });
}
