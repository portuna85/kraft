import type { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/shared/config/env", () => ({
  serverEnv: {
    backendInternalUrl: "http://backend:8080",
    webObservabilitySecret: "test-web-observability-secret",
  },
}));

import { parseReport, POST } from "./route";

describe("CSP 보고서 파싱", () => {
  it("표준 report-uri 형식을 파싱한다", () => {
    const body = {
      "csp-report": {
        "document-uri": "https://kraft.io.kr/",
        "violated-directive": "script-src",
        "blocked-uri": "https://evil.example",
        "source-file": "https://kraft.io.kr/app.js",
      },
    };
    expect(parseReport(body)).toEqual({
      documentUri: "https://kraft.io.kr/",
      violatedDirective: "script-src",
      blockedUri: "https://evil.example",
      sourceFile: "https://kraft.io.kr/app.js",
    });
  });

  it("violated-directive가 없으면 effective-directive로 대체한다", () => {
    const body = {
      "csp-report": { "effective-directive": "style-src" },
    };
    expect(parseReport(body)?.violatedDirective).toBe("style-src");
  });

  it("csp-report 필드가 없으면 null을 돌려준다", () => {
    expect(parseReport({})).toBeNull();
    expect(parseReport(null)).toBeNull();
  });

  it("긴 문자열 필드는 잘라낸다", () => {
    const long = "a".repeat(500);
    const body = { "csp-report": { "document-uri": long } };
    expect(parseReport(body)?.documentUri.length).toBe(300);
  });
});

function request(body: string): NextRequest {
  return new Request("http://localhost/api/csp-report", {
    method: "POST",
    headers: { "content-type": "application/csp-report" },
    body,
  }) as NextRequest;
}

describe("POST /api/csp-report — 백엔드 push(OBS-WEB-01)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("violatedDirective만 백엔드로 push한다(documentUri 등은 안 보낸다)", async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);
    const body = JSON.stringify({
      "csp-report": {
        "document-uri": "https://kraft.io.kr/",
        "violated-directive": "script-src",
        "blocked-uri": "https://evil.example",
      },
    });

    const response = await POST(request(body));

    expect(response.status).toBe(204);
    expect(fetchSpy).toHaveBeenCalledWith(
      "http://backend:8080/api/v1/observability/csp-violations",
      expect.objectContaining({ method: "POST" }),
    );
    const init = fetchSpy.mock.calls[0]?.[1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({ violatedDirective: "script-src" });
  });

  it("백엔드 push가 실패해도 route 응답은 영향받지 않는다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));
    const body = JSON.stringify({ "csp-report": { "violated-directive": "style-src" } });

    const response = await POST(request(body));

    expect(response.status).toBe(204);
  });
});
