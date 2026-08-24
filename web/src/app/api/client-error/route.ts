import { NextRequest } from "next/server";

import { guardCollectRequest } from "@/shared/lib/collect-request";
import { pushObservabilityEvent } from "@/shared/lib/push-observability-event";

/**
 * 클라이언트 렌더 오류 수집
 *
 * 세그먼트 `error.tsx`는 클라이언트 컴포넌트라 서버 로그(운영 관측)에 자동으로 남지
 * 않는다 — `console.error`만 하면 사용자 브라우저 콘솔에만 남고 아무도 못 본다.
 */
const MAX_BODY_BYTES = 4_096;
const MAX_STRING_LENGTH = 500;
const RATE_LIMIT_PER_MINUTE = 30;

type ClientErrorPayload = {
  message: string;
  digest?: string;
  route: string;
};

function truncate(value: unknown): string {
  if (typeof value !== "string") return "";
  return value.length > MAX_STRING_LENGTH ? value.slice(0, MAX_STRING_LENGTH) : value;
}

export function isValidPayload(body: unknown): body is ClientErrorPayload {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return (
    typeof b.message === "string" &&
    b.message.length > 0 &&
    typeof b.route === "string" &&
    b.route.length > 0
  );
}

export async function POST(request: NextRequest) {
  const guard = await guardCollectRequest(request, {
    routeKey: "client-error",
    rateLimitPerMinute: RATE_LIMIT_PER_MINUTE,
    maxBodyBytes: MAX_BODY_BYTES,
    allowedContentTypes: ["application/json", "text/plain"],
  });
  if (!guard.ok) return guard.response;

  let body: unknown;
  try {
    body = JSON.parse(guard.text);
  } catch {
    return new Response(null, { status: 400 });
  }

  if (!isValidPayload(body)) {
    return new Response(null, { status: 400 });
  }

  console.error("[client-side-render-error]", {
    message: truncate(body.message),
    digest: body.digest !== undefined ? truncate(body.digest) : undefined,
    route: truncate(body.route),
  });

  // OBS-WEB-01: 메트릭에는 route만 쓴다 — message/digest는 카디널리티 무한대라
  // WebObservabilityMetrics가 애초에 받지도 않는다.
  pushObservabilityEvent("client-errors", { route: truncate(body.route) });

  return new Response(null, { status: 204 });
}
