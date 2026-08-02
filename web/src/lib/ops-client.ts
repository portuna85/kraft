/**
 * `/ops` 운영 화면 전용 전송 계층.
 *
 * FE-079: 이전에는 컴포넌트 안에서 `await response.json()`을 무조건 호출했다.
 * 그래서 204 No Content나 프록시가 돌려준 HTML 오류 페이지처럼 JSON이 아닌 응답이 오면
 * 파싱이 던지고 catch로 빠져 전부 "네트워크 오류가 발생했습니다"로 표시됐다 —
 * 실제로는 성공했거나 서버가 이유를 알려 준 상황인데 원인을 감췄다.
 * 타임아웃도 없어 백엔드가 응답하지 않으면 버튼이 계속 잠긴 채로 남았다.
 */

/** 다른 브라우저 호출과 같은 기준(lib/browser-api.ts)을 쓴다. */
const OPS_TIMEOUT_MS = 5000;

export type OpsResult<T> = { ok: true; data: T } | { ok: false; message: string };

function isJson(response: Response): boolean {
  const contentType = response.headers.get("content-type") ?? "";
  return contentType.includes("json");
}

export async function callOps<T>(url: string, init?: RequestInit): Promise<OpsResult<T>> {
  let response: Response;
  try {
    response = await fetch(url, {
      cache: "no-store",
      signal: AbortSignal.timeout(OPS_TIMEOUT_MS),
      ...init,
    });
  } catch (error) {
    // AbortSignal.timeout()은 TimeoutError로 중단한다 — 응답이 없는 것과 구분해 알린다.
    if (error instanceof DOMException && error.name === "TimeoutError") {
      return { ok: false, message: "응답이 없어 요청을 중단했습니다. 잠시 후 다시 시도하세요." };
    }
    return { ok: false, message: "네트워크 오류가 발생했습니다." };
  }

  // 204/205처럼 본문이 없는 성공 응답. 이전에는 여기서 파싱이 던져 실패로 뒤집혔다.
  if (response.status === 204 || response.status === 205) {
    return response.ok
      ? { ok: true, data: undefined as T }
      : { ok: false, message: `요청에 실패했습니다. (HTTP ${response.status})` };
  }

  if (!isJson(response)) {
    if (response.ok) {
      // 성공인데 JSON이 아니면 화면이 기대하는 데이터가 없다 — 조용히 성공 처리하지 않는다.
      return { ok: false, message: "예상과 다른 형식의 응답을 받았습니다." };
    }
    return { ok: false, message: `요청에 실패했습니다. (HTTP ${response.status})` };
  }

  let body: unknown;
  try {
    body = await response.json();
  } catch {
    return { ok: false, message: "응답을 해석하지 못했습니다." };
  }

  if (!response.ok) {
    const message = (body as { message?: string } | null)?.message;
    return { ok: false, message: message ?? `요청에 실패했습니다. (HTTP ${response.status})` };
  }
  return { ok: true, data: body as T };
}
