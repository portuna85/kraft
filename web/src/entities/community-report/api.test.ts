import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { emptyResponse, headersOf, initOf, mockFetch, urlOf } from "@/shared/api/test/fetch-mock";

import { reportContent } from "./api";

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("reportContent", () => {
  it("신고 대상·사유를 본문에 실어 POST한다", async () => {
    const spy = mockFetch(emptyResponse(200));

    await reportContent("POST", 1, "SPAM");

    expect(urlOf(spy)).toBe("/api/v1/community/reports");
    const init = initOf(spy) as { method?: string; body?: string };
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body ?? "{}")).toEqual({ targetType: "POST", targetId: 1, reason: "SPAM" });
  });

  it("CSRF 헤더를 자동으로 붙인다", async () => {
    const spy = mockFetch(emptyResponse(200));

    await reportContent("COMMENT", 2, "HARASSMENT");

    expect(headersOf(spy)["X-XSRF-TOKEN"]).toBe("csrf-xyz");
  });

  it("응답 본문 형태는 검증하지 않는다(v.unknown) — 임의 형태를 그대로 통과시킨다", async () => {
    mockFetch(new Response(JSON.stringify({ anything: [1, 2, 3] }), { status: 200 }));

    await expect(reportContent("USER", 3, "OTHER")).resolves.toEqual({ anything: [1, 2, 3] });
  });
});
