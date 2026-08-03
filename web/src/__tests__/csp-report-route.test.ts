import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetRateLimitForTests } from "@/lib/rate-limit";

const warnSpy = vi.fn();

vi.mock("@/lib/logger", () => ({
  default: { warn: (...args: unknown[]) => warnSpy(...args) },
}));

function request(body: unknown, headers?: Record<string, string>) {
  return new Request("http://localhost/api/csp-report", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
    headers,
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
    resetRateLimitForTests();
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

  // H-2: 이 라우트도 백엔드 PublicRateLimitFilter가 도달할 수 없어 자체 방어가 필요하다.
  it("Content-Length가 상한을 넘으면 본문을 읽지 않고 413을 반환한다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    const res = await POST(request(VALID_BODY, { "content-length": "999999" }));

    expect(res.status).toBe(413);
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it("허용되지 않은 Content-Type은 415를 반환한다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    const res = await POST(request(VALID_BODY, { "content-type": "multipart/form-data" }));

    expect(res.status).toBe(415);
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it("한 IP가 분당 한도를 넘으면 429를 반환한다", async () => {
    const { POST } = await import("@/app/api/csp-report/route");
    const headers = { "x-forwarded-for": "203.0.113.20" };
    let lastStatus = 0;
    for (let i = 0; i < 31; i++) {
      lastStatus = (await POST(request(VALID_BODY, headers))).status;
    }

    expect(lastStatus).toBe(429);
  });
});
