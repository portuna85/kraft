import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

describe("백엔드 프록시", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    global.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it("선택된 백엔드 헤더와 본문을 그대로 전달한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "Cache-Control": "public, max-age=60, must-revalidate",
          ETag: "\"abc123\"",
          "X-Request-Id": "req-1",
          "X-RateLimit-Limit": "120",
          "X-RateLimit-Remaining": "119",
        },
      })
    ) as typeof fetch;

    const { proxyBackend } = await import("@/lib/backend-proxy");
    const response = await proxyBackend("/api/v1/stats/frequency");

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("cache-control")).toContain("max-age=60");
    expect(response.headers.get("etag")).toBe("\"abc123\"");
    expect(response.headers.get("x-request-id")).toBe("req-1");
    expect(response.headers.get("x-ratelimit-limit")).toBe("120");
    expect(response.headers.get("x-ratelimit-remaining")).toBe("119");
    await expect(response.json()).resolves.toEqual({ ok: true });
  });

  it("백엔드 요청이 실패하면 502 응답을 반환한다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("timeout")) as typeof fetch;

    const { proxyBackend } = await import("@/lib/backend-proxy");
    const response = await proxyBackend("/api/v1/stats/frequency");

    expect(response.status).toBe(502);
    await expect(response.json()).resolves.toMatchObject({
      code: "BACKEND_UNAVAILABLE",
    });
  });

  it("기기 범위 요청에는 토큰과 필요한 JSON 헤더만 전달한다", async () => {
    const { deviceProxyHeaders } = await import("@/lib/backend-proxy");
    const headers = new Headers(deviceProxyHeaders(new Request("http://localhost", {
      headers: { "X-Device-Token": "device-token", Cookie: "must-not-forward" },
    }), true));

    expect(headers.get("x-device-token")).toBe("device-token");
    expect(headers.get("content-type")).toBe("application/json");
    expect(headers.get("cookie")).toBeNull();
  });
});
