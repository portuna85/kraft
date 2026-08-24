import type { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/shared/config/env", () => ({
  serverEnv: {
    backendInternalUrl: "http://backend:8080",
    webObservabilitySecret: "test-web-observability-secret",
  },
}));

import { isValidPayload, POST } from "./route";

describe("클라이언트 오류 페이로드 검증", () => {
  it("message와 route가 있으면 통과시킨다", () => {
    expect(isValidPayload({ message: "boom", route: "/" })).toBe(true);
  });

  it("digest는 선택 필드다", () => {
    expect(isValidPayload({ message: "boom", route: "/", digest: "abc123" })).toBe(true);
  });

  it("message나 route가 비어 있으면 막는다", () => {
    expect(isValidPayload({ message: "", route: "/" })).toBe(false);
    expect(isValidPayload({ message: "boom", route: "" })).toBe(false);
  });

  it("객체가 아니면 막는다", () => {
    expect(isValidPayload(null)).toBe(false);
    expect(isValidPayload("boom")).toBe(false);
  });
});

function request(body: string): NextRequest {
  return new Request("http://localhost/api/client-error", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
  }) as NextRequest;
}

describe("POST /api/client-error — 백엔드 push(OBS-WEB-01)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("route만 백엔드로 push한다(message·digest는 안 보낸다)", async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);
    const body = JSON.stringify({ message: "boom", route: "/recommend", digest: "abc123" });

    const response = await POST(request(body));

    expect(response.status).toBe(204);
    expect(fetchSpy).toHaveBeenCalledWith(
      "http://backend:8080/api/v1/observability/client-errors",
      expect.objectContaining({ method: "POST" }),
    );
    const init = fetchSpy.mock.calls[0]?.[1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({ route: "/recommend" });
  });

  it("백엔드 push가 실패해도 route 응답은 영향받지 않는다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));
    const body = JSON.stringify({ message: "boom", route: "/" });

    const response = await POST(request(body));

    expect(response.status).toBe(204);
  });
});
