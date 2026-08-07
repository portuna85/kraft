import { getDeviceToken } from "@/lib/device-token";
import { composeAbortSignal } from "@/lib/transport-signal";

export class BrowserApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "BrowserApiError";
  }
}

// M-1: X-Device-Token 조립이 9곳의 호출부에 흩어져 있었다(각자 getDeviceToken()을
// import해 headers 객체를 직접 만듦). 전부 device-token이 필요한 요청이라는 걸
// 표시만 하면 되므로, 조립 자체를 여기로 옮긴다. 모든 요청에 필요한 건 아니므로
// (예: 로그인 여부만 확인하는 조회) 기본값은 붙이지 않는다 — 명시적 옵트인.
export type BrowserFetchInit = RequestInit & { includeDeviceToken?: boolean };

export async function browserFetch<T>(
  url: string,
  init?: BrowserFetchInit,
  isValid?: (body: unknown) => boolean,
): Promise<T> {
  const { includeDeviceToken, headers, signal, ...rest } = init ?? {};
  const finalHeaders = new Headers(headers);
  if (includeDeviceToken) {
    finalHeaders.set("X-Device-Token", getDeviceToken());
  }
  const res = await fetch(url, {
    ...rest,
    headers: finalHeaders,
    signal: composeAbortSignal(5000, signal),
  });
  let body: unknown;
  try {
    body = await res.json();
  } catch {
    body = undefined;
  }
  if (!res.ok) {
    const { code, message } = (body ?? {}) as {
      code?: string;
      message?: string;
    };
    throw new BrowserApiError(code ?? "UNKNOWN", message ?? res.statusText ?? "");
  }
  if (isValid && !isValid(body)) {
    throw new BrowserApiError("INVALID_RESPONSE_SHAPE", `${url} 응답 형식이 예상과 다릅니다.`);
  }
  return body as T;
}
