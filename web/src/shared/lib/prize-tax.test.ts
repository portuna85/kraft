import { describe, expect, it } from "vitest";

import { calculateNetPrize } from "./prize-tax";

describe("세후 실수령액 계산", () => {
  it("200만원 이하는 비과세로 그대로 돌려준다", () => {
    expect(calculateNetPrize(0)).toBe(0);
    expect(calculateNetPrize(2_000_000)).toBe(2_000_000);
  });

  it("200만원 초과 ~ 3억원 구간은 초과분에 22%를 뗀다", () => {
    // (10,000,000 - 2,000,000) * 0.22 = 1,760,000
    expect(calculateNetPrize(10_000_000)).toBe(8_240_000);
  });

  it("정확히 3억원 경계에서도 22% 구간만 적용된다", () => {
    // (300,000,000 - 2,000,000) * 0.22 = 65,560,000
    expect(calculateNetPrize(300_000_000)).toBe(234_440_000);
  });

  it("3억원 초과 구간은 초과분에만 33%를 적용해 누진 합산한다", () => {
    // 저구간: (300,000,000 - 2,000,000) * 0.22 = 65,560,000
    // 고구간: (1,000,000,000 - 300,000,000) * 0.33 = 231,000,000
    expect(calculateNetPrize(1_000_000_000)).toBe(703_440_000);
  });
});
