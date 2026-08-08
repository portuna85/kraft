import * as v from "valibot";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { CSRF_HEADER_NAME } from "./csrf";
import { DEVICE_TOKEN_HEADER_NAME, DEVICE_TOKEN_STORAGE_KEY } from "./device-token";
import { ApiError } from "./error";
import { browserMutate, browserQuery, serverFetch } from "./transport";

const schema = v.object({ id: v.number(), name: v.string() });

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function mockFetch(response: Response | Promise<Response>) {
  const spy = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", spy);
  return spy;
}

function headersOf(spy: ReturnType<typeof vi.fn>): Record<string, string> {
  const init = spy.mock.calls[0]?.[1] as RequestInit | undefined;
  return (init?.headers ?? {}) as Record<string, string>;
}

beforeEach(() => {
  window.localStorage.clear();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("serverFetch", () => {
  it("계약에 맞는 응답을 파싱해 돌려준다", async () => {
    mockFetch(jsonResponse({ id: 1, name: "회차" }));

    const result = await serverFetch("/api/v1/x", schema, {
      cache: { mode: "revalidate", seconds: 60, tags: ["rounds:latest"] },
    });

    expect(result).toEqual({ id: 1, name: "회차" });
  });

  it("재검증 주기와 캐시 태그를 fetch에 그대로 넘긴다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "회차" }));

    await serverFetch("/api/v1/x", schema, {
      cache: { mode: "revalidate", seconds: 1800, tags: ["stats:all"] },
    });

    const init = spy.mock.calls[0]?.[1] as { next?: { revalidate: number; tags: string[] } };
    expect(init.next).toEqual({ revalidate: 1800, tags: ["stats:all"] });
  });

  it("no-store 전략에서는 next 옵션 대신 cache: no-store를 쓴다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "회차" }));

    await serverFetch("/api/v1/x", schema, { cache: { mode: "no-store" } });

    const init = spy.mock.calls[0]?.[1] as { cache?: string; next?: unknown };
    expect(init.cache).toBe("no-store");
    expect(init.next).toBeUndefined();
  });

  it("기본은 GET이고 본문을 붙이지 않는다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "회차" }));

    await serverFetch("/api/v1/x", schema, { cache: { mode: "no-store" } });

    const init = spy.mock.calls[0]?.[1] as { method?: string; body?: unknown };
    expect(init.method).toBe("GET");
    expect(init.body).toBeUndefined();
  });

  it("POST 조회는 본문과 content-type을 함께 보낸다", async () => {
    // 부수효과가 없는데 백엔드가 POST로 받는 경우가 있다(조합 분석). 본문만 실리고
    // content-type이 빠지면 백엔드가 415를 낸다.
    const spy = mockFetch(jsonResponse({ id: 1, name: "회차" }));

    await serverFetch("/api/v1/x", schema, {
      method: "POST",
      body: { numbers: [1, 2, 3, 4, 5, 6] },
      cache: { mode: "no-store" },
    });

    const init = spy.mock.calls[0]?.[1] as {
      method?: string;
      body?: string;
      headers?: Record<string, string>;
    };
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ numbers: [1, 2, 3, 4, 5, 6] }));
    expect(init.headers?.["content-type"]).toBe("application/json");
  });

  it("스키마와 어긋난 응답을 통과시키지 않는다", async () => {
    // 현행의 얕은 타입가드는 numbers 요소 타입까지 보지 않아 이런 응답을 통과시켰다.
    mockFetch(jsonResponse({ id: "1", name: "회차" }));

    await expect(
      serverFetch("/api/v1/x", schema, { cache: { mode: "no-store" } }),
    ).rejects.toMatchObject({ kind: "server", code: "SCHEMA_MISMATCH" });
  });

  it("백엔드 오류 코드와 상태를 보존한다", async () => {
    mockFetch(jsonResponse({ code: "ROUND_NOT_FOUND", message: "없는 회차입니다." }, 404));

    const error = await serverFetch("/api/v1/x", schema, {
      cache: { mode: "no-store" },
    }).catch((e: unknown) => e as ApiError);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ kind: "client", code: "ROUND_NOT_FOUND", status: 404 });
  });

  it("5xx는 server 종류로 분류한다", async () => {
    mockFetch(jsonResponse({ message: "boom" }, 503));

    await expect(
      serverFetch("/api/v1/x", schema, { cache: { mode: "no-store" } }),
    ).rejects.toMatchObject({ kind: "server", status: 503 });
  });

  it("연결 실패를 network 종류로 정규화한다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("failed to fetch")));

    await expect(
      serverFetch("/api/v1/x", schema, { cache: { mode: "no-store" } }),
    ).rejects.toMatchObject({ kind: "network" });
  });
});

describe("browserQuery", () => {
  it("사용자별 응답이 공유 캐시에 담기지 않도록 no-store로 보낸다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "나" }));

    await browserQuery("/api/v1/me", schema);

    const init = spy.mock.calls[0]?.[1] as RequestInit;
    expect(init.cache).toBe("no-store");
    expect(init.credentials).toBe("same-origin");
  });

  it("익명 스코프를 요청했을 때만 디바이스 토큰 헤더를 붙인다", async () => {
    window.localStorage.setItem(DEVICE_TOKEN_STORAGE_KEY, "token-abc");

    const withScope = mockFetch(jsonResponse({ id: 1, name: "나" }));
    await browserQuery("/api/v1/saved", schema, { deviceScoped: true });
    expect(headersOf(withScope)[DEVICE_TOKEN_HEADER_NAME]).toBe("token-abc");

    vi.unstubAllGlobals();

    const withoutScope = mockFetch(jsonResponse({ id: 1, name: "나" }));
    await browserQuery("/api/v1/community/me/saved-numbers", schema);
    expect(headersOf(withoutScope)[DEVICE_TOKEN_HEADER_NAME]).toBeUndefined();
  });
});

describe("browserMutate", () => {
  it("쓰기 요청에 CSRF 헤더를 자동으로 붙인다", async () => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
    const spy = mockFetch(jsonResponse({ id: 1, name: "저장됨" }));

    await browserMutate("/api/v1/saved", schema, { method: "POST", body: { numbers: [1] } });

    expect(headersOf(spy)[CSRF_HEADER_NAME]).toBe("csrf-xyz");
  });

  it("CSRF 쿠키가 없으면 요청을 보내지 않고 원인을 알려준다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "저장됨" }));

    await expect(
      browserMutate("/api/v1/saved", schema, { method: "POST", body: {} }),
    ).rejects.toMatchObject({ code: "CSRF_TOKEN_MISSING" });
    expect(spy).not.toHaveBeenCalled();
  });

  it("본문이 없으면 content-type을 붙이지 않는다", async () => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
    const spy = mockFetch(jsonResponse({ id: 1, name: "삭제됨" }));

    await browserMutate("/api/v1/saved/1", schema, { method: "DELETE" });

    expect(headersOf(spy)["content-type"]).toBeUndefined();
  });

  it("409를 낙관적 잠금 충돌로 식별할 수 있게 한다", async () => {
    document.cookie = "XSRF-TOKEN=csrf-xyz; path=/";
    mockFetch(jsonResponse({ code: "VERSION_CONFLICT" }, 409));

    const error = await browserMutate("/api/v1/community/posts/1", schema, {
      method: "PUT",
      body: {},
    }).then(
      () => null,
      (cause: unknown) => cause as ApiError,
    );

    expect(error?.isConflict).toBe(true);
    expect(error?.isUnauthenticated).toBe(false);
  });
});
