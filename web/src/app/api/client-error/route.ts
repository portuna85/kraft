import { NextRequest } from "next/server";
import logger from "@/lib/logger";
import { clientIpFromHeaders, isRateLimited } from "@/lib/rate-limit";

// M-3: 세그먼트 error.tsx는 클라이언트 컴포넌트라 instrumentation.ts가 세운 pino
// 파이프라인에 직접 쓸 수 없다 — console.error만 하면 서버 로그(운영 관측)에 전혀
// 남지 않는다. /api/vitals, /api/csp-report와 동일한 이유로 Caddy의 /api/v1/* 프록시
// 대상이 아니라 백엔드 PublicRateLimitFilter가 도달할 수 없다 — 본문 상한·속도 제한을
// 여기서 직접 건다.
const MAX_BODY_BYTES = 4_096;
const MAX_STRING_LENGTH = 500;
const ALLOWED_CONTENT_TYPES = ["application/json", "text/plain"];
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

function isValidPayload(body: unknown): body is ClientErrorPayload {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return typeof b.message === "string" && b.message.length > 0 && typeof b.route === "string" && b.route.length > 0;
}

export async function POST(req: NextRequest) {
  const ip = clientIpFromHeaders(req.headers);
  if (isRateLimited(`client-error:${ip}`, RATE_LIMIT_PER_MINUTE)) {
    return new Response(null, { status: 429 });
  }

  const contentLength = req.headers.get("content-length");
  if (contentLength && Number(contentLength) > MAX_BODY_BYTES) {
    return new Response(null, { status: 413 });
  }
  const contentType = req.headers.get("content-type") ?? "";
  if (contentType && !ALLOWED_CONTENT_TYPES.some((allowed) => contentType.startsWith(allowed))) {
    return new Response(null, { status: 415 });
  }

  let body: unknown;
  try {
    const text = await req.text();
    if (text.length > MAX_BODY_BYTES) {
      return new Response(null, { status: 413 });
    }
    body = JSON.parse(text);
  } catch {
    return new Response(null, { status: 400 });
  }

  if (!isValidPayload(body)) {
    return new Response(null, { status: 400 });
  }

  logger.error(
    {
      message: truncate(body.message),
      digest: body.digest ? truncate(body.digest) : undefined,
      route: truncate(body.route),
    },
    "client-side-render-error"
  );

  return new Response(null, { status: 204 });
}
