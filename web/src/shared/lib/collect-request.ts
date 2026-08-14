import type { NextRequest } from "next/server";

import { clientIpFromHeaders, isRateLimited } from "./rate-limit";

/**
 * 소형 공개 수집 엔드포인트 공통 가드
 *
 * `/api/vitals`·`/api/csp-report`·`/api/client-error`는 셋 다 같은 방어선을 쓴다:
 * IP별 분당 상한, 본문 크기 상한(선언된 Content-Length와 실측 길이 둘 다), 허용
 * Content-Type. JSON 파싱과 페이로드 검증은 라우트마다 모양이 달라 호출부에 남긴다.
 *
 * Content-Length가 있으면 본문을 읽기 전에 먼저 거부해 디코드 비용을 아낀다 — 다만
 * Fetch 표준상 Content-Length가 항상 있는 것은 아니므로(예: chunked 전송) 없다고
 * 거부하지 않는다. 최종 방어선은 실제로 읽은 텍스트 길이 검사다.
 */
export async function guardCollectRequest(
  request: NextRequest,
  options: {
    routeKey: string;
    rateLimitPerMinute: number;
    maxBodyBytes: number;
    allowedContentTypes: readonly string[];
  },
): Promise<{ ok: true; text: string } | { ok: false; response: Response }> {
  const ip = clientIpFromHeaders(request.headers);
  if (isRateLimited(`${options.routeKey}:${ip}`, options.rateLimitPerMinute)) {
    return { ok: false, response: new Response(null, { status: 429 }) };
  }

  const contentLength = request.headers.get("content-length");
  if (contentLength !== null && Number(contentLength) > options.maxBodyBytes) {
    return { ok: false, response: new Response(null, { status: 413 }) };
  }

  const contentType = request.headers.get("content-type") ?? "";
  if (
    contentType !== "" &&
    !options.allowedContentTypes.some((allowed) => contentType.startsWith(allowed))
  ) {
    return { ok: false, response: new Response(null, { status: 415 }) };
  }

  // navigator.sendBeacon은 Content-Type을 text/plain으로 보낼 수 있어 req.json() 대신
  // 텍스트로 받아 호출부가 직접 파싱한다.
  const text = await request.text();
  if (text.length > options.maxBodyBytes) {
    return { ok: false, response: new Response(null, { status: 413 }) };
  }

  return { ok: true, text };
}
