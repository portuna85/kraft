import { NextRequest } from "next/server";

import { guardCollectRequest } from "@/shared/lib/collect-request";
import { pushObservabilityEvent } from "@/shared/lib/push-observability-event";

/**
 * CSP 위반 수신처
 *
 * FE-CSP-01(docs/improvement.md): 과거엔 Report-Only 후보 정책만 여기로 보냈지만,
 * 그 후보(unsafe-inline 없는 style-src)가 이제 실제 강제 정책이다 —
 * `shared/config/csp.ts`의 `buildCsp`가 이 경로로 계속 위반을 보고한다. 브라우저가
 * report-uri로 POST하는 형식(Content-Type: application/csp-report, 본문은
 * `{"csp-report": {...}}`)만 지원한다. referrer·script-sample처럼 페이지 내용이
 * 섞일 수 있는 필드는 남기지 않는다(vitals 라우트와 같은 원칙).
 */
const MAX_STRING_LENGTH = 300;
const MAX_BODY_BYTES = 20_480;
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

export function parseReport(body: unknown): CspReport | null {
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

export async function POST(request: NextRequest) {
  const guard = await guardCollectRequest(request, {
    routeKey: "csp-report",
    rateLimitPerMinute: RATE_LIMIT_PER_MINUTE,
    maxBodyBytes: MAX_BODY_BYTES,
    allowedContentTypes: ["application/csp-report", "application/json", "text/plain"],
  });
  if (!guard.ok) return guard.response;

  let body: unknown;
  try {
    body = JSON.parse(guard.text);
  } catch {
    return new Response(null, { status: 400 });
  }

  const report = parseReport(body);
  if (report === null) {
    return new Response(null, { status: 400 });
  }

  console.warn("[csp-violation]", report);

  // OBS-WEB-01: 메트릭에는 violatedDirective만 쓴다 — documentUri/blockedUri/
  // sourceFile은 카디널리티가 없는 백엔드 집계에 필요 없다.
  pushObservabilityEvent("csp-violations", { violatedDirective: report.violatedDirective });

  return new Response(null, { status: 204 });
}
