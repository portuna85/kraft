/**
 * API 오류 정규화
 *
 * 현행의 강점을 그대로 승계한다(§6.5): 백엔드가 준 오류 코드와 상태를 버리지 않는다.
 * 그래야 오류 UI가 "실패했습니다" 한 줄로 뭉개지지 않고 원인별로 분기할 수 있다.
 */

/** 호출부가 분기에 쓰는 4종. 이 이상 늘리면 화면마다 처리가 갈라진다. */
export type ApiErrorKind =
  /** 네트워크 도달 실패 — 재시도 가치가 있다 */
  | "network"
  /** 5초 타임아웃(§13.2) 또는 호출부 abort */
  | "timeout"
  /** 4xx — 요청이 잘못됐거나 권한이 없다. 재시도해도 같다 */
  | "client"
  /** 5xx 또는 스키마 위반 — 서버 쪽 문제 */
  | "server";

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  /** 백엔드가 준 오류 코드. 없으면 null — 임의의 문자열로 채우지 않는다 */
  readonly code: string | null;
  /** HTTP 상태. 네트워크·타임아웃이면 null */
  readonly status: number | null;

  constructor(
    kind: ApiErrorKind,
    message: string,
    options?: { code?: string | null; status?: number | null; cause?: unknown },
  ) {
    super(message, { cause: options?.cause });
    this.name = "ApiError";
    this.kind = kind;
    this.code = options?.code ?? null;
    this.status = options?.status ?? null;
  }

  /** 로그인 유도 대상인지. 403(CSRF 만료)과 구분해야 한다 — §15.5 */
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }

  /** 새로고침 후 재시도 안내 대상인지 */
  get isForbidden(): boolean {
    return this.status === 403;
  }

  /** 낙관적 잠금 충돌 — 호출부가 최신 재조회로 분기한다(§16.4) */
  get isConflict(): boolean {
    return this.status === 409;
  }
}

/** 백엔드 오류 응답 본문에서 코드·메시지를 최대한 살려낸다. 실패해도 던지지 않는다. */
export function readBackendError(body: unknown): { code: string | null; message: string | null } {
  if (typeof body !== "object" || body === null) return { code: null, message: null };
  const record = body as Record<string, unknown>;
  return {
    code: typeof record.code === "string" ? record.code : null,
    message: typeof record.message === "string" ? record.message : null,
  };
}

export function toApiError(cause: unknown, fallbackMessage: string): ApiError {
  if (cause instanceof ApiError) return cause;
  if (cause instanceof DOMException && cause.name === "AbortError") {
    return new ApiError("timeout", "요청이 시간 내에 끝나지 않았습니다.", { cause });
  }
  return new ApiError("network", fallbackMessage, { cause });
}
