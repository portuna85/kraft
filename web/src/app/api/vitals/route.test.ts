import type { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/shared/config/env", () => ({
  serverEnv: {
    backendInternalUrl: "http://backend:8080",
    webObservabilitySecret: "test-web-observability-secret",
  },
}));

import { isValidPayload, POST } from "./route";

const VALID = {
  name: "LCP",
  value: 1200,
  rating: "good",
  route: "/",
  deviceClass: "mobile",
  layoutClass: "compact",
  release: "v1",
};

describe("vitals 페이로드 검증", () => {
  it("정상 페이로드를 통과시킨다", () => {
    expect(isValidPayload(VALID)).toBe(true);
  });

  it("화이트리스트 밖 지표 이름을 막는다", () => {
    expect(isValidPayload({ ...VALID, name: "FID" })).toBe(false);
  });

  it("화이트리스트 밖 등급을 막는다", () => {
    expect(isValidPayload({ ...VALID, rating: "bad" })).toBe(false);
  });

  it("음수·비유한 값을 막는다", () => {
    expect(isValidPayload({ ...VALID, value: -1 })).toBe(false);
    expect(isValidPayload({ ...VALID, value: Infinity })).toBe(false);
  });

  it("route가 비어 있으면 막는다", () => {
    expect(isValidPayload({ ...VALID, route: "" })).toBe(false);
  });

  // RSP-38(docs/improvement.md): deviceClass(640/1024px)와 실제 셸 전환(1152px)이
  // 어긋나 1024~1151px 탭바 UI가 desktop에 섞였다 — layoutClass가 그 상태를
  // 별도로 반영한다.
  it("화이트리스트 밖 layoutClass를 막는다", () => {
    expect(isValidPayload({ ...VALID, layoutClass: "tablet" })).toBe(false);
  });

  it("layoutClass가 없으면 막는다", () => {
    const { layoutClass: _layoutClass, ...withoutLayoutClass } = VALID;
    expect(isValidPayload(withoutLayoutClass)).toBe(false);
  });

  it("desktop-nav layoutClass를 통과시킨다", () => {
    expect(isValidPayload({ ...VALID, layoutClass: "desktop-nav" })).toBe(true);
  });

  it("객체가 아니면 막는다", () => {
    expect(isValidPayload(null)).toBe(false);
    expect(isValidPayload("LCP")).toBe(false);
  });
});

function request(body: string): NextRequest {
  return new Request("http://localhost/api/vitals", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
  }) as NextRequest;
}

describe("POST /api/vitals — 백엔드 push(OBS-WEB-01)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("유효한 요청을 백엔드 관측 endpoint로 push한다(release는 보내지 않는다)", async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchSpy);

    const response = await POST(request(JSON.stringify(VALID)));

    expect(response.status).toBe(204);
    expect(fetchSpy).toHaveBeenCalledWith(
      "http://backend:8080/api/v1/observability/web-vitals",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "X-Web-Observability-Secret": "test-web-observability-secret",
        }),
      }),
    );
    const init = fetchSpy.mock.calls[0]?.[1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({
      name: "LCP",
      value: 1200,
      rating: "good",
      route: "/",
      deviceClass: "mobile",
      layoutClass: "compact",
    });
  });

  it("백엔드 push가 실패해도 route 응답은 영향받지 않는다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));

    const response = await POST(request(JSON.stringify(VALID)));

    expect(response.status).toBe(204);
  });
});
