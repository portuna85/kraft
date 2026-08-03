import { NextRequest } from "next/server";
import logger from "@/lib/logger";
import { clientIpFromHeaders, isRateLimited } from "@/lib/rate-limit";

// CSP Report-Only 채널(web/src/proxy.ts의 buildCspReportOnly)이 위반을 보고하는 수신처.
// 브라우저가 report-uri로 POST하는 레거시 형식(Content-Type: application/csp-report,
// 본문은 {"csp-report": {...}})만 지원한다 — 최소 필드만 화이트리스트로 통과시키고
// referrer·script-sample처럼 페이지 내용이 섞일 수 있는 필드는 남기지 않는다(vitals
// 라우트와 동일한 원칙, /api/vitals/route.ts 참고).
const MAX_STRING_LENGTH = 300;

// H-2: /api/vitals와 동일한 이유로 백엔드 PublicRateLimitFilter가 도달할 수 없다.
const MAX_BODY_BYTES = 20_480;
const ALLOWED_CONTENT_TYPES = ["application/csp-report", "application/json", "text/plain"];
const RATE_LIMIT_PER_MINUTE = 30;

type CspReport = {
  documentUri: string;
  violatedDirective: string;
  blockedUri: string;
  sourceFile: string;
};

function truncate(value: unknown): string {
  if (typeof value !== "string") return "";
  return value.length > MAX_STRING_LENGTH ? value.slice(0, MAX_STRING_LENGTH) : value;
}

function parseReport(body: unknown): CspReport | null {
  if (typeof body !== "object" || body === null) return null;
  const report = (body as Record<string, unknown>)["csp-report"];
  if (typeof report !== "object" || report === null) return null;
  const r = report as Record<string, unknown>;
  return {
    documentUri: truncate(r["document-uri"]),
    violatedDirective: truncate(r["violated-directive"] ?? r["effective-directive"]),
    blockedUri: truncate(r["blocked-uri"]),
    sourceFile: truncate(r["source-file"]),
  };
}

export async function POST(req: NextRequest) {
  const ip = clientIpFromHeaders(req.headers);
  if (isRateLimited(`csp-report:${ip}`, RATE_LIMIT_PER_MINUTE)) {
    return new Response(null, { status: 429 });
  }

  // Content-Length가 있으면 본문을 읽기 전에 미리 거부한다 — 없다고 거부하지는 않는다
  // (Fetch 표준상 항상 보장되지 않음). 최종 방어선은 아래 req.text() 이후의 실측 길이 검사다.
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

  const report = parseReport(body);
  if (!report) {
    return new Response(null, { status: 400 });
  }

  logger.warn(report, "csp-report-only-violation");

  return new Response(null, { status: 204 });
}
