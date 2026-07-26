import { NextRequest } from "next/server";
import logger from "@/lib/logger";

// F-06: Core Web Vitals 필드 데이터 수집. 절대 개인정보(IP, User-Agent, 쿠키, 세션/
// 사용자 식별자)를 로그에 남기지 않는다 — 화이트리스트된 필드만 통과시킨다.
const METRIC_NAMES = new Set(["LCP", "INP", "CLS"]);
const RATINGS = new Set(["good", "needs-improvement", "poor"]);
const DEVICE_CLASSES = new Set(["mobile", "tablet", "desktop"]);
const MAX_ROUTE_LENGTH = 200;
const MAX_RELEASE_LENGTH = 100;

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
  let body: unknown;
  try {
    // navigator.sendBeacon은 Content-Type을 text/plain으로 보낼 수 있어 req.json()
    // 대신 텍스트로 받아 직접 파싱한다.
    body = JSON.parse(await req.text());
  } catch {
    return new Response(null, { status: 400 });
  }

  if (!isValidPayload(body)) {
    return new Response(null, { status: 400 });
  }

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

  return new Response(null, { status: 204 });
}
