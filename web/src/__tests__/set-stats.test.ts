import { describe, expect, it } from "vitest";
import { computeSetStats, formatSetStatsSummary } from "@/features/recommendation/set-stats";

describe("computeSetStats", () => {
  it("연속 번호가 없고 5개 구간에 고르게 퍼진 조합을 계산한다", () => {
    const stats = computeSetStats([1, 7, 15, 23, 38, 45]);

    expect(stats).toEqual({
      oddCount: 5,
      evenCount: 1,
      lowCount: 3,
      highCount: 3,
      sum: 129,
      consecutivePairCount: 0,
      decadeSpreadCount: 5,
    });
    expect(formatSetStatsSummary(stats)).toBe(
      "홀짝 5:1 · 고저 3:3 · 합계 129 · 연속번호 없음 · 구간 5개 분포",
    );
  });

  it("연속 번호 5쌍과 1개 구간에 몰린 조합을 계산한다", () => {
    const stats = computeSetStats([1, 2, 3, 4, 5, 6]);

    expect(stats).toEqual({
      oddCount: 3,
      evenCount: 3,
      lowCount: 6,
      highCount: 0,
      sum: 21,
      consecutivePairCount: 5,
      decadeSpreadCount: 1,
    });
    expect(formatSetStatsSummary(stats)).toBe(
      "홀짝 3:3 · 고저 6:0 · 합계 21 · 연속번호 5쌍 · 구간 1개 분포",
    );
  });

  it("정렬되지 않은 입력도 정렬 후 동일하게 계산한다", () => {
    const sorted = computeSetStats([1, 2, 3, 4, 5, 6]);
    const shuffled = computeSetStats([6, 3, 1, 5, 2, 4]);

    expect(shuffled).toEqual(sorted);
  });

  it("고저 경계값(22/23)을 저번호/고번호 기준과 일치시킨다", () => {
    const stats = computeSetStats([1, 2, 3, 4, 22, 23]);

    expect(stats.lowCount).toBe(5);
    expect(stats.highCount).toBe(1);
  });
});
