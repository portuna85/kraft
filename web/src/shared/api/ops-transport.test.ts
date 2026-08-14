import * as v from "valibot";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "./error";
import { opsMutate, opsQuery } from "./ops-transport";

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

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("opsQuery", () => {
  it("계약에 맞는 응답을 파싱해 돌려준다", async () => {
    mockFetch(jsonResponse({ id: 1, name: "요약" }));

    const result = await opsQuery("/ops-api/summary", schema, "secret-token");

    expect(result).toEqual({ id: 1, name: "요약" });
  });

  it("X-Ops-Token 헤더를 붙이고 no-store로 보낸다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "요약" }));

    await opsQuery("/ops-api/summary", schema, "secret-token");

    const init = spy.mock.calls[0]?.[1] as RequestInit;
    expect(init.cache).toBe("no-store");
    expect(headersOf(spy)["X-Ops-Token"]).toBe("secret-token");
  });

  it("스키마와 어긋난 응답을 통과시키지 않는다", async () => {
    mockFetch(jsonResponse({ id: "1", name: "요약" }));

    await expect(opsQuery("/ops-api/summary", schema, "secret-token")).rejects.toMatchObject({
      kind: "server",
      code: "SCHEMA_MISMATCH",
    });
  });

  it("백엔드 오류 코드와 상태를 보존한다", async () => {
    mockFetch(
      jsonResponse({ code: "OPS_UNAUTHORIZED", message: "토큰이 올바르지 않습니다." }, 401),
    );

    const error = await opsQuery("/ops-api/summary", schema, "wrong-token").catch(
      (e: unknown) => e as ApiError,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ kind: "client", code: "OPS_UNAUTHORIZED", status: 401 });
  });

  it("5xx는 server 종류로 분류한다", async () => {
    mockFetch(jsonResponse({ message: "boom" }, 503));

    await expect(opsQuery("/ops-api/summary", schema, "secret-token")).rejects.toMatchObject({
      kind: "server",
      status: 503,
    });
  });

  it("TD-009: 순수 연결 실패는 network 종류로 정규화한다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("failed to fetch")));

    await expect(opsQuery("/ops-api/summary", schema, "secret-token")).rejects.toMatchObject({
      kind: "network",
    });
  });

  it("TD-009: abort/timeout은 network가 아니라 timeout 종류로 분류한다", async () => {
    // 수정 전에는 opsRequest가 모든 fetch 실패를 무조건 kind:"network"로 던졌다 —
    // TD-001에서 늘린 20초 수집 타임아웃이 실제로 걸려도 잘못 분류됐다. toApiError로
    // 통합한 뒤에는 transport.ts의 serverFetch/browserQuery와 동일하게 AbortError를
    // kind:"timeout"으로 구분해야 한다.
    const abortError = new DOMException("The operation was aborted.", "AbortError");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(abortError));

    await expect(opsQuery("/ops-api/summary", schema, "secret-token")).rejects.toMatchObject({
      kind: "timeout",
    });
  });
});

describe("opsMutate", () => {
  it("POST 요청에 X-Ops-Token 헤더와 본문을 함께 보낸다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "수집됨" }));

    await opsMutate("/ops-api/collect/latest", schema, {
      token: "secret-token",
      method: "POST",
    });

    const init = spy.mock.calls[0]?.[1] as { method?: string };
    expect(init.method).toBe("POST");
    expect(headersOf(spy)["X-Ops-Token"]).toBe("secret-token");
  });

  it("본문이 없으면 content-type을 붙이지 않는다", async () => {
    const spy = mockFetch(jsonResponse({ id: 1, name: "수집됨" }));

    await opsMutate("/ops-api/collect/latest", schema, { token: "secret-token", method: "POST" });

    expect(headersOf(spy)["content-type"]).toBeUndefined();
  });

  it("timeoutMs를 지정하지 않으면 기본 5초 타임아웃을 쓴다", async () => {
    mockFetch(jsonResponse({ id: 1, name: "수집됨" }));
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout");

    await opsMutate("/ops-api/rounds", schema, { token: "secret-token", method: "POST" });

    expect(timeoutSpy).toHaveBeenCalledWith(5_000);
  });

  it("timeoutMs를 지정하면 그 값으로 abort signal을 구성한다", async () => {
    mockFetch(jsonResponse({ id: 1, name: "수집됨" }));
    const timeoutSpy = vi.spyOn(AbortSignal, "timeout");

    await opsMutate("/ops-api/collect/latest", schema, {
      token: "secret-token",
      method: "POST",
      timeoutMs: 20_000,
    });

    expect(timeoutSpy).toHaveBeenCalledWith(20_000);
  });
});
