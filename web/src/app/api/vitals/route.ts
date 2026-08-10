import { NextRequest } from "next/server";

import { guardCollectRequest } from "@/shared/lib/collect-request";

/**
 * Core Web Vitals 필드 데이터 수집 — improvement_fe.md §19.5
 *
 * 절대 개인정보(IP, User-Agent, 쿠키, 세션/사용자 식별자)를 로그에 남기지 않는다 —
 * 화이트리스트된 필드만 통과시킨다.
 */
const METRIC_NAMES = new Set(["LCP", "INP", "CLS"]);
const RATINGS = new Set(["good", "needs-improvement", "poor"]);
const DEVICE_CLASSES = new Set(["mobile", "tablet", "desktop"]);
const MAX_ROUTE_LENGTH = 200;
const MAX_RELEASE_LENGTH = 100;
const MAX_BODY_BYTES = 2_048;
const RATE_LIMIT_PER_MINUTE = 60;
/**
 * 유효한 요청도 전부 로깅하면 소량 레코드를 무한히 보내 실제 로그를 밀어낼 수 있다
 * (로그 회전이 조용히 신호를 지운다) — 표본만 남긴다.
 */
const SAMPLE_RATE = 0.2;

type VitalsPayload = {
  name: string;
  value: number;
  rating: string;
  route: string;
  deviceClass: string;
  release: string;
};

export function isValidPayload(body: unknown): body is VitalsPayload {
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

export async function POST(request: NextRequest) {
  const guard = await guardCollectRequest(request, {
    routeKey: "vitals",
    rateLimitPerMinute: RATE_LIMIT_PER_MINUTE,
    maxBodyBytes: MAX_BODY_BYTES,
    allowedContentTypes: ["text/plain", "application/json"],
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

  if (Math.random() < SAMPLE_RATE) {
    // F-06: Lighthouse는 랩 진단일 뿐이고 운영 판단의 근거는 이 실사용자 필드
    // 데이터(RUM)여야 한다(§19.5).
    console.info("[web-vitals]", {
      name: body.name,
      value: body.value,
      rating: body.rating,
      route: body.route,
      deviceClass: body.deviceClass,
      release: body.release,
    });
  }

  return new Response(null, { status: 204 });
}
