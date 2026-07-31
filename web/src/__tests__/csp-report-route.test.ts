import { beforeEach, describe, expect, it, vi } from "vitest";

const warnSpy = vi.fn();

vi.mock("@/lib/logger", () => ({
  default: { warn: (...args: unknown[]) => warnSpy(...args) },
}));

function request(body: unknown) {
  return new Request("http://localhost/api/csp-report", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
  }) as unknown as import("next/server").NextRequest;
}

const VALID_BODY = {
  "csp-report": {
    "document-uri": "https://kraft.io.kr/frequency",
    "referrer": "https://kraft.io.kr/",
    "violated-directive": "style-src-elem",
    "effective-directive": "style-src-elem",
    "original-policy": "default-src 'self'",
    "disposition": "report",
    "blocked-uri": "inline",
    "line-number": 12,
    "source-file": "https://kraft.io.kr/frequency",
    "status-code": 200,
    "script-sample": "",
  },
};

describe("POST /api/csp-report", () => {
  beforeEach(() => {
    warnSpy.mockClear();
  });

  it("정상 리포트는 204를 반환하고 화이트리스트된 필드만 로그로 남긴다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    const res = await POST(request(VALID_BODY));

    expect(res.status).toBe(204);
    expect(warnSpy).toHaveBeenCalledWith(
      {
        documentUri: "https://kraft.io.kr/frequency",
        violatedDirective: "style-src-elem",
        blockedUri: "inline",
        sourceFile: "https://kraft.io.kr/frequency",
      },
      "csp-report-only-violation"
    );
  });

  it("로그 인자에 referrer·script-sample 등 페이지 내용이 섞일 수 있는 필드를 남기지 않는다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    await POST(request(VALID_BODY));

    const loggedArgs = warnSpy.mock.calls[0][0];
    expect(Object.keys(loggedArgs).sort()).toEqual([
      "blockedUri",
      "documentUri",
      "sourceFile",
      "violatedDirective",
    ]);
    expect(loggedArgs).not.toHaveProperty("referrer");
    expect(loggedArgs).not.toHaveProperty("scriptSample");
  });

  it("csp-report 필드가 없으면 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    const res = await POST(request({ foo: "bar" }));

    expect(res.status).toBe(400);
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it("JSON이 아닌 본문(malformed)은 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    const res = await POST(request("not json"));

    expect(res.status).toBe(400);
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it("과도하게 긴 문자열 필드는 잘라서 로그로 남긴다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    const longUri = "https://kraft.io.kr/" + "a".repeat(400);
    await POST(
      request({
        "csp-report": { ...VALID_BODY["csp-report"], "document-uri": longUri },
      })
    );

    const loggedArgs = warnSpy.mock.calls[0][0];
    expect(loggedArgs.documentUri.length).toBe(300);
  });
});
