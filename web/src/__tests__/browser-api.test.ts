import { beforeEach, describe, expect, it, vi } from "vitest";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";

describe("browserFetch", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("요청마다 타임아웃용 AbortSignal을 함께 보낸다(F-05: API 타임아웃)", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({}),
    });
    global.fetch = fetchSpy;

    await browserFetch("/api/v1/rounds/latest");

    const [, init] = fetchSpy.mock.calls[0];
    expect(init.signal).toBeInstanceOf(AbortSignal);
  });

  it("요청이 타임아웃(abort)되면 오류를 그대로 전파한다(BrowserApiError로 감싸지 않는다)", async () => {
    const abortError = new DOMException("The operation was aborted.", "AbortError");
    global.fetch = vi.fn().mockRejectedValue(abortError);

    await expect(browserFetch("/api/v1/rounds/latest")).rejects.toBe(abortError);
  });

  it("정상 응답의 JSON 바디를 그대로 반환한다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ round: 1230 }),
    });

    await expect(browserFetch("/api/v1/rounds/latest")).resolves.toEqual({ round: 1230 });
  });

  it("오류 응답에 code·message가 있으면 그대로 BrowserApiError에 담는다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      statusText: "Conflict",
      json: () => Promise.resolve({ code: "COMMUNITY_POST_VERSION_CONFLICT", message: "충돌났습니다" }),
    });

    await expect(browserFetch("/api/v1/community/posts/1")).rejects.toMatchObject({
      code: "COMMUNITY_POST_VERSION_CONFLICT",
      message: "충돌났습니다",
    });
  });

  it("B-02: 오류 계약에 없는 새 필드가 추가돼도 code·message 파싱은 깨지지 않는다(전방 호환)", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      statusText: "Conflict",
      json: () =>
        Promise.resolve({
          timestamp: "2026-01-01T00:00:00Z",
          status: 409,
          error: "Conflict",
          code: "COMMUNITY_POST_VERSION_CONFLICT",
          message: "충돌났습니다",
          path: "/api/v1/community/posts/1",
          // 계약에 없는 미래 필드 — 무시돼야 한다.
          traceId: "abc-123",
          retryable: false,
        }),
    });

    await expect(browserFetch("/api/v1/community/posts/1")).rejects.toMatchObject({
      code: "COMMUNITY_POST_VERSION_CONFLICT",
      message: "충돌났습니다",
    });
  });

  it("오류 응답 바디가 JSON이 아니면(malformed) code는 UNKNOWN, message는 statusText로 대체한다", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      statusText: "Bad Gateway",
      json: () => Promise.reject(new SyntaxError("Unexpected token < in JSON")),
    });

    await expect(browserFetch("/api/v1/rounds/latest")).rejects.toMatchObject({
      code: "UNKNOWN",
      message: "Bad Gateway",
    });
  });

  it("오류 응답에 message도 statusText도 없으면 빈 문자열 메시지가 된다(호출부에서 falsy 처리 주의)", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: "",
      json: () => Promise.resolve({}),
    });

    let caught: unknown;
    try {
      await browserFetch("/api/v1/rounds/latest");
    } catch (err) {
      caught = err;
    }

    expect(caught).toBeInstanceOf(BrowserApiError);
    expect((caught as BrowserApiError).message).toBe("");
  });
});
