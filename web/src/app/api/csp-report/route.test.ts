import { describe, expect, it } from "vitest";

import { parseReport } from "./route";

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
