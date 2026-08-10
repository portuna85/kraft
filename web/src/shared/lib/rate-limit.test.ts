import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { clientIpFromHeaders, isRateLimited, resetRateLimitForTests } from "./rate-limit";

describe("요청 제한", () => {
  beforeEach(() => {
    resetRateLimitForTests();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("한도 안에서는 제한하지 않는다", () => {
    for (let i = 0; i < 5; i += 1) {
      expect(isRateLimited("key", 5)).toBe(false);
    }
  });

  it("한도를 넘으면 제한한다", () => {
    for (let i = 0; i < 5; i += 1) isRateLimited("key", 5);
    expect(isRateLimited("key", 5)).toBe(true);
  });

  it("키가 다르면 서로 영향을 주지 않는다", () => {
    for (let i = 0; i < 5; i += 1) isRateLimited("a", 5);
    expect(isRateLimited("b", 5)).toBe(false);
  });

  it("윈도우가 지나면 다시 허용한다", () => {
    for (let i = 0; i < 5; i += 1) isRateLimited("key", 5);
    expect(isRateLimited("key", 5)).toBe(true);

    vi.advanceTimersByTime(60_001);

    expect(isRateLimited("key", 5)).toBe(false);
  });
});

describe("클라이언트 IP 추출", () => {
  it("X-Forwarded-For의 첫 값을 쓴다", () => {
    const headers = new Headers({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" });
    expect(clientIpFromHeaders(headers)).toBe("1.2.3.4");
  });

  it("헤더가 없으면 unknown을 돌려준다", () => {
    expect(clientIpFromHeaders(new Headers())).toBe("unknown");
  });
});
