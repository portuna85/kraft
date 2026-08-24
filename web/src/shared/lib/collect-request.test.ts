import { NextRequest } from "next/server";
import { afterEach, describe, expect, it } from "vitest";

import { guardCollectRequest } from "./collect-request";
import { resetRateLimitForTests } from "./rate-limit";

function request(options: { body?: string; contentLength?: string; contentType?: string }) {
  const headers: Record<string, string> = {};
  if (options.contentLength !== undefined) headers["content-length"] = options.contentLength;
  if (options.contentType !== undefined) headers["content-type"] = options.contentType;
  return new NextRequest("http://placeholder.invalid/api/vitals", {
    method: "POST",
    headers,
    body: options.body,
  });
}

const BASE_OPTIONS = {
  routeKey: "test-route",
  rateLimitPerMinute: 1000,
  allowedContentTypes: ["text/plain"] as const,
};

describe("guardCollectRequest — 본문 크기 상한(UTF-8 byte 기준)", () => {
  afterEach(() => {
    resetRateLimitForTests();
  });

  it("ASCII 본문이 byte 상한 이내면 통과한다", async () => {
    const result = await guardCollectRequest(request({ body: "0123456789" }), {
      ...BASE_OPTIONS,
      maxBodyBytes: 10,
    });
    expect(result.ok).toBe(true);
  });

  it("ASCII 본문이 byte 상한을 초과하면 413을 반환한다", async () => {
    const result = await guardCollectRequest(request({ body: "01234567890" }), {
      ...BASE_OPTIONS,
      maxBodyBytes: 10,
    });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.response.status).toBe(413);
  });

  // FE-API-01 회귀 방지: 한글 5글자는 UTF-16 code unit 수(length)로는 5이지만 UTF-8로는
  // 글자당 3바이트씩 15바이트다. 예전 구현(text.length 비교)은 이 페이로드를 10바이트
  // 상한 아래로 잘못 통과시켰다 — byte 기준으로 고쳐야 정확히 거부된다.
  it("한글 본문은 UTF-16 length가 아니라 실제 UTF-8 byte로 상한을 검사한다", async () => {
    const body = "가나다라마"; // length === 5, UTF-8 byte length === 15
    expect(body.length).toBe(5);
    expect(new TextEncoder().encode(body).byteLength).toBe(15);

    const result = await guardCollectRequest(request({ body }), {
      ...BASE_OPTIONS,
      maxBodyBytes: 10,
    });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.response.status).toBe(413);
  });

  it("한글 본문이 실제 byte 상한 이내면 통과한다", async () => {
    const body = "가나다라마"; // 15 bytes
    const result = await guardCollectRequest(request({ body }), {
      ...BASE_OPTIONS,
      maxBodyBytes: 15,
    });
    expect(result.ok).toBe(true);
  });

  it("emoji(서로게이트 쌍)도 UTF-8 byte 기준으로 검사한다", async () => {
    const body = "😀😀😀"; // length === 6 (서로게이트 쌍 3개), UTF-8 byte length === 12
    expect(body.length).toBe(6);
    expect(new TextEncoder().encode(body).byteLength).toBe(12);

    const result = await guardCollectRequest(request({ body }), {
      ...BASE_OPTIONS,
      maxBodyBytes: 6,
    });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.response.status).toBe(413);
  });

  it("Content-Length 헤더가 상한을 초과하면 본문을 읽기 전에 413을 반환한다", async () => {
    const result = await guardCollectRequest(
      request({ body: "0123456789", contentLength: "999" }),
      { ...BASE_OPTIONS, maxBodyBytes: 10 },
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.response.status).toBe(413);
  });

  it("Content-Length 헤더가 없어도 실측 byte 길이로 검사한다", async () => {
    const result = await guardCollectRequest(request({ body: "01234567890" }), {
      ...BASE_OPTIONS,
      maxBodyBytes: 10,
    });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.response.status).toBe(413);
  });

  it("Content-Length 헤더가 위조돼 실제보다 작아도 실측 byte로 최종 거부한다", async () => {
    const result = await guardCollectRequest(
      request({ body: "01234567890", contentLength: "1" }),
      { ...BASE_OPTIONS, maxBodyBytes: 10 },
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.response.status).toBe(413);
  });
});
