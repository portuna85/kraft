import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { expectedFrequency, frequencyRatio, frequencyStatsSchema } from "./schema";

describe("무작위 기대값", () => {
  it("회차마다 45개 중 6개가 뽑히는 비율로 계산한다", () => {
    // 이 값이 없으면 "152회 나왔다"가 많은 건지 적은 건지 화면에서 알 수 없다(M-6).
    expect(expectedFrequency(45)).toBeCloseTo(6);
    expect(expectedFrequency(1150)).toBeCloseTo(153.33, 2);
  });

  it("집계 회차가 0이면 비교 자체가 불가능하므로 배율이 null이다", () => {
    expect(frequencyRatio(0, 0)).toBeNull();
  });

  it("기대값 대비 배율을 돌려준다", () => {
    expect(frequencyRatio(12, 45)).toBeCloseTo(2);
  });
});

describe("빈도 통계 스키마", () => {
  const VALID = {
    totalRounds: 1150,
    frequencies: [{ ballNumber: 17, frequency: 152, lastRound: 1148 }],
    topSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
    bottomSix: { balls: [], wonFirstPrize: false, firstPrizeHistory: [] },
  };

  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(frequencyStatsSchema, VALID).success).toBe(true);
  });

  it("번호 범위를 벗어난 항목을 막는다", () => {
    const result = v.safeParse(frequencyStatsSchema, {
      ...VALID,
      frequencies: [{ ballNumber: 46, frequency: 1, lastRound: 1 }],
    });
    expect(result.success).toBe(false);
  });

  it("음수 출현 횟수를 막는다", () => {
    const result = v.safeParse(frequencyStatsSchema, {
      ...VALID,
      frequencies: [{ ballNumber: 17, frequency: -1, lastRound: 1 }],
    });
    expect(result.success).toBe(false);
  });
});
