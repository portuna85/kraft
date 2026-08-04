import { beforeEach, describe, expect, it, vi } from "vitest";
import { callOps } from "@/lib/ops-client";

function response(init: { status: number; body?: unknown; contentType?: string | null }) {
  const headers = new Headers();
  if (init.contentType) headers.set("content-type", init.contentType);
  return {
    ok: init.status >= 200 && init.status < 300,
    status: init.status,
    headers,
    json: () => Promise.resolve(init.body),
  };
}

// FE-079: 예전에는 무조건 response.json()을 호출해, 본문이 없거나 JSON이 아닌 응답이
// 전부 "네트워크 오류"로 뒤집혔다. 성공을 실패로 보고하거나 서버가 준 이유를 감췄다.
describe("운영 API 호출", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("JSON 성공 응답의 본문을 그대로 돌려준다", async () => {
    global.fetch = vi.fn().mockResolvedValue(
      response({ status: 200, body: { service: "kraft" }, contentType: "application/json" })
    );

    const result = await callOps<{ service: string }>("/ops-api/summary");

    expect(result).toEqual({ ok: true, data: { service: "kraft" } });
  });

  it("204 No Content를 실패로 뒤집지 않는다", async () => {
    global.fetch = vi.fn().mockResolvedValue(response({ status: 204, contentType: null }));

    const result = await callOps("/ops-api/rounds");

    expect(result.ok).toBe(true);
  });

  it("JSON이 아닌 오류 응답(HTML 등)도 네트워크 오류로 뭉뚱그리지 않는다", async () => {
    global.fetch = vi.fn().mockResolvedValue(
      response({ status: 502, body: "<html>bad gateway</html>", contentType: "text/html" })
    );

    const result = await callOps("/ops-api/summary");

    expect(result).toEqual({ ok: false, message: "요청에 실패했습니다. (HTTP 502)" });
  });

  it("서버가 준 오류 메시지를 그대로 전달한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(
      response({ status: 401, body: { message: "운영 토큰이 올바르지 않습니다." }, contentType: "application/json" })
    );

    const result = await callOps("/ops-api/summary");

    expect(result).toEqual({ ok: false, message: "운영 토큰이 올바르지 않습니다." });
  });

  it("성공인데 JSON이 아니면 조용히 성공 처리하지 않는다", async () => {
    global.fetch = vi.fn().mockResolvedValue(
      response({ status: 200, body: "<html>login</html>", contentType: "text/html" })
    );

    const result = await callOps("/ops-api/summary");

    expect(result).toEqual({ ok: false, message: "예상과 다른 형식의 응답을 받았습니다." });
  });

  it("응답이 없으면 타임아웃으로 중단하고 그 사실을 알린다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new DOMException("timed out", "TimeoutError"));

    const result = await callOps("/ops-api/summary");

    expect(result).toEqual({
      ok: false,
      message: "응답이 없어 요청을 중단했습니다. 잠시 후 다시 시도하세요.",
    });
  });

  it("그 밖의 네트워크 실패는 네트워크 오류로 알린다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError("failed to fetch"));

    const result = await callOps("/ops-api/summary");

    expect(result).toEqual({ ok: false, message: "네트워크 오류가 발생했습니다." });
  });

  it("요청에 타임아웃 시그널과 no-store를 실어 보낸다", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      response({ status: 200, body: {}, contentType: "application/json" })
    );
    global.fetch = fetchMock;

    await callOps("/ops-api/summary", { headers: { "X-Ops-Token": "t" } });

    const init = fetchMock.mock.calls[0]![1];
    expect(init.cache).toBe("no-store");
    expect(init.signal).toBeInstanceOf(AbortSignal);
    expect(init.headers).toEqual({ "X-Ops-Token": "t" });
  });
});
