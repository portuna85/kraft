import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/shared/config/env", () => ({
  serverEnv: {
    backendInternalUrl: "http://backend:8080",
    webObservabilitySecret: "test-secret",
  },
}));

import { pushObservabilityEvent } from "./push-observability-event";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("pushObservabilityEvent", () => {
  it("백엔드 관측 endpoint로 시크릿 헤더와 함께 POST한다", () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);

    pushObservabilityEvent("web-vitals", { name: "LCP" });

    expect(fetchSpy).toHaveBeenCalledWith(
      "http://backend:8080/api/v1/observability/web-vitals",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "content-type": "application/json",
          "X-Web-Observability-Secret": "test-secret",
        }),
        body: JSON.stringify({ name: "LCP" }),
      }),
    );
  });

  it("fetch가 reject해도 던지지 않는다", () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("boom")));

    expect(() => pushObservabilityEvent("web-vitals", {})).not.toThrow();
  });
});

describe("pushObservabilityEvent — 시크릿 미설정", () => {
  it("시크릿이 없으면 fetch를 아예 부르지 않는다", async () => {
    vi.doMock("@/shared/config/env", () => ({
      serverEnv: { backendInternalUrl: "http://backend:8080", webObservabilitySecret: undefined },
    }));
    vi.resetModules();
    const { pushObservabilityEvent: push } = await import("./push-observability-event");
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);

    push("web-vitals", {});

    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
