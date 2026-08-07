import { NextRequest } from "next/server";
import logger from "@/lib/logger";
import { clientIpFromHeaders, isRateLimited } from "@/lib/rate-limit";

// F-06: Core Web Vitals 필드 데이터 수집. 절대 개인정보(IP, User-Agent, 쿠키, 세션/
// 사용자 식별자)를 로그에 남기지 않는다 — 화이트리스트된 필드만 통과시킨다.
const METRIC_NAMES = new Set(["LCP", "INP", "CLS"]);
const RATINGS = new Set(["good", "needs-improvement", "poor"]);
const DEVICE_CLASSES = new Set(["mobile", "tablet", "desktop"]);
const MAX_ROUTE_LENGTH = 200;
const MAX_RELEASE_LENGTH = 100;

// H-2: 이 엔드포인트는 Caddy의 /api/v1/* 프록시 대상이 아니라 백엔드 PublicRateLimitFilter가
// 도달할 수 없다 — 본문 상한·속도 제한·샘플링을 여기서 직접 건다. 페이로드가 몇 개 필드뿐인
// 작은 JSON이라 상한을 낮게 둔다.
const MAX_BODY_BYTES = 2_048;
const ALLOWED_CONTENT_TYPES = ["text/plain", "application/json"];
const RATE_LIMIT_PER_MINUTE = 60;
// 유효한 요청도 전부 로깅하면 공격자가 유효한 소량 레코드를 무한히 보내 실제 로그를
// 밀어낼 수 있다(로그 회전이 조용히 실제 신호를 지운다) — 표본만 남긴다.
const SAMPLE_RATE = 0.2;

type VitalsPayload = {
  name: string;
  value: number;
  rating: string;
  route: string;
  deviceClass: string;
  release: string;
};

function isValidPayload(body: unknown): body is VitalsPayload {
  if (typeof body !== "object" || body === null) return false;
  const b = body as Record<string, unknown>;
  return (
    typeof b.name === "string" &&
    METRIC_NAMES.has(b.name) &&
    typeof b.value === "number" &&
    Number.isFinite(b.value) &&
    b.value >= 0 &&
    typeof b.rating === "string" &&
    RATINGS.has(b.rating) &&
    typeof b.route === "string" &&
    b.route.length > 0 &&
    b.route.length <= MAX_ROUTE_LENGTH &&
    typeof b.deviceClass === "string" &&
    DEVICE_CLASSES.has(b.deviceClass) &&
    typeof b.release === "string" &&
    b.release.length <= MAX_RELEASE_LENGTH
  );
}

export async function POST(req: NextRequest) {
  const ip = clientIpFromHeaders(req.headers);
  if (isRateLimited(`vitals:${ip}`, RATE_LIMIT_PER_MINUTE)) {
    return new Response(null, { status: 429 });
  }

  // Content-Length가 있으면(대부분의 실제 요청에 있다) 본문을 읽기 전에 미리 거부해
  // 디코드 비용을 아낀다 — 다만 Fetch 표준상 Content-Length는 항상 보장되지 않으므로
  // (예: chunked 전송) 없다고 거부하지 않는다. 최종 방어선은 아래 req.text() 이후의
  // 실측 길이 검사다.
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
    // navigator.sendBeacon은 Content-Type을 text/plain으로 보낼 수 있어 req.json()
    // 대신 텍스트로 받아 직접 파싱한다.
    const text = await req.text();
    if (text.length > MAX_BODY_BYTES) {
      // Content-Length를 신뢰할 수 없는 경우(누락·위조)에 대한 방어선.
      return new Response(null, { status: 413 });
    }
    body = JSON.parse(text);
  } catch {
    return new Response(null, { status: 400 });
  }

  if (!isValidPayload(body)) {
    return new Response(null, { status: 400 });
  }

  if (Math.random() < SAMPLE_RATE) {
    logger.info(
      {
        name: body.name,
        value: body.value,
        rating: body.rating,
        route: body.route,
        deviceClass: body.deviceClass,
        release: body.release,
      },
      "web-vitals"
    );
  }

  return new Response(null, { status: 204 });
}
