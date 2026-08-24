import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { CSRF_HEADER_NAME } from "@/shared/api/csrf";

import { bootstrapCsrf, logout } from "./api";

function mockFetch(response: Response | Promise<Response>) {
  const spy = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", spy);
  return spy;
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("logout", () => {
  it("CSRF 쿠키가 없으면 요청을 보내지 않고 실패로 본다", async () => {
    const spy = mockFetch(new Response(null, { status: 200 }));

    const ok = await logout();

    expect(ok).toBe(false);
    expect(spy).not.toHaveBeenCalled();
  });

  it("CSRF 헤더를 붙여 POST /logout을 보낸다", async () => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
    const spy = mockFetch(new Response(null, { status: 200 }));

    const ok = await logout();

    expect(ok).toBe(true);
    const [url, init] = spy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/logout");
    expect(init.method).toBe("POST");
    expect((init.headers as Record<string, string>)[CSRF_HEADER_NAME]).toBe("csrf-xyz");
  });

  it("응답이 실패면 false를 돌려준다", async () => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
    mockFetch(new Response(null, { status: 403 }));

    expect(await logout()).toBe(false);
  });

  it("네트워크 오류를 던지지 않고 false로 흡수한다", async () => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("failed to fetch")));

    expect(await logout()).toBe(false);
  });
});

describe("bootstrapCsrf", () => {
  it("GET /api/v1/community/csrf를 same-origin으로 부른다", async () => {
    const spy = mockFetch(new Response(null, { status: 204 }));

    await bootstrapCsrf();

    expect(spy).toHaveBeenCalledTimes(1);
    const [url, init] = spy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/community/csrf");
    expect(init.method).toBe("GET");
    expect(init.credentials).toBe("same-origin");
  });

  it("네트워크 오류를 던지지 않고 흡수한다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("failed to fetch")));

    await expect(bootstrapCsrf()).resolves.toBeUndefined();
  });

  it("실패 응답(4xx/5xx)이어도 던지지 않는다", async () => {
    mockFetch(new Response(null, { status: 503 }));

    await expect(bootstrapCsrf()).resolves.toBeUndefined();
  });
});
