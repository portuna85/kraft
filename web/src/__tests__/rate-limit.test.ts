import { beforeEach, describe, expect, it } from "vitest";
import { clientIpFromHeaders, isRateLimited, resetRateLimitForTests } from "@/lib/rate-limit";

describe("isRateLimited", () => {
  beforeEach(() => {
    resetRateLimitForTests();
  });

  it("한도 이하면 계속 허용한다", () => {
    for (let i = 0; i < 5; i++) {
      expect(isRateLimited("key-a", 5)).toBe(false);
    }
  });

  it("한도를 넘으면 차단한다", () => {
    for (let i = 0; i < 5; i++) {
      isRateLimited("key-b", 5);
    }
    expect(isRateLimited("key-b", 5)).toBe(true);
  });

  it("서로 다른 키는 독립된 카운터를 쓴다", () => {
    for (let i = 0; i < 5; i++) {
      isRateLimited("key-c", 5);
    }
    // key-c는 한도 초과 직전이지만, 별개 키인 key-d는 영향을 받지 않는다.
    expect(isRateLimited("key-d", 5)).toBe(false);
  });
});

describe("clientIpFromHeaders", () => {
  it("X-Forwarded-For의 첫 번째 IP를 클라이언트로 본다", () => {
    const headers = new Headers({ "x-forwarded-for": "203.0.113.7, 10.0.0.1" });
    expect(clientIpFromHeaders(headers)).toBe("203.0.113.7");
  });

  it("헤더가 없으면 unknown으로 폴백한다", () => {
    expect(clientIpFromHeaders(new Headers())).toBe("unknown");
  });
});
