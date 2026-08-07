import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetRateLimitForTests } from "@/lib/rate-limit";

const errorSpy = vi.fn();

vi.mock("@/lib/logger", () => ({
  default: { error: (...args: unknown[]) => errorSpy(...args) },
}));

function request(body: unknown, headers?: Record<string, string>) {
  return new Request("http://localhost/api/client-error", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
    headers,
  }) as unknown as import("next/server").NextRequest;
}

const VALID_BODY = { message: "데이터를 가져오지 못했습니다", digest: "abc123", route: "/community" };

// M-3: error.tsx는 클라이언트 컴포넌트라 서버 pino 파이프라인에 직접 쓸 수 없다 —
// 이 라우트가 그 다리 역할을 한다. 이 라우트가 없다면 클라이언트 렌더 실패가
// console.error에만 남고 서버 로그에는 전혀 남지 않는다.
describe("POST /api/client-error", () => {
  beforeEach(() => {
    errorSpy.mockClear();
    resetRateLimitForTests();
  });

  it("정상 리포트는 204를 반환하고 error 레벨로 로그를 남긴다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const res = await POST(request(VALID_BODY));

    expect(res.status).toBe(204);
    expect(errorSpy).toHaveBeenCalledWith(
      { message: "데이터를 가져오지 못했습니다", digest: "abc123", route: "/community" },
      "client-side-render-error"
    );
  });

  it("digest가 없어도 통과한다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const res = await POST(request({ message: "실패", route: "/saved" }));

    expect(res.status).toBe(204);
    expect(errorSpy).toHaveBeenCalledWith(
      { message: "실패", digest: undefined, route: "/saved" },
      "client-side-render-error"
    );
  });

  it("message 또는 route가 없으면 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const res = await POST(request({ message: "실패" }));

    expect(res.status).toBe(400);
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it("JSON이 아닌 본문(malformed)은 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const res = await POST(request("not json"));

    expect(res.status).toBe(400);
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it("과도하게 긴 message는 잘라서 로그로 남긴다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const longMessage = "a".repeat(1000);
    await POST(request({ message: longMessage, route: "/community" }));

    const loggedArgs = errorSpy.mock.calls[0]![0];
    expect(loggedArgs.message.length).toBe(500);
  });

  it("Content-Length가 상한을 넘으면 본문을 읽지 않고 413을 반환한다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const res = await POST(request(VALID_BODY, { "content-length": "999999" }));

    expect(res.status).toBe(413);
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it("허용되지 않은 Content-Type은 415를 반환한다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const res = await POST(request(VALID_BODY, { "content-type": "multipart/form-data" }));

    expect(res.status).toBe(415);
    expect(errorSpy).not.toHaveBeenCalled();
  });

  it("한 IP가 분당 한도를 넘으면 429를 반환한다", async () => {
    const { POST } = await import("@/app/api/client-error/route");
    const headers = { "x-forwarded-for": "203.0.113.30" };
    let lastStatus = 0;
    for (let i = 0; i < 31; i++) {
      lastStatus = (await POST(request(VALID_BODY, headers))).status;
    }

    expect(lastStatus).toBe(429);
  });
});
