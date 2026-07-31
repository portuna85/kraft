import { NextRequest } from "next/server";
import logger from "@/lib/logger";

// CSP Report-Only 채널(web/src/proxy.ts의 buildCspReportOnly)이 위반을 보고하는 수신처.
// 브라우저가 report-uri로 POST하는 레거시 형식(Content-Type: application/csp-report,
// 본문은 {"csp-report": {...}})만 지원한다 — 최소 필드만 화이트리스트로 통과시키고
// referrer·script-sample처럼 페이지 내용이 섞일 수 있는 필드는 남기지 않는다(vitals
// 라우트와 동일한 원칙, /api/vitals/route.ts 참고).
const MAX_STRING_LENGTH = 300;

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
  let body: unknown;
  try {
    body = JSON.parse(await req.text());
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
