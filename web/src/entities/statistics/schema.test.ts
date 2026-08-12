import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import {
  analysisSchema,
  bucketTotal,
  coOccurrenceRatio,
  companionStatsSchema,
  COUNT_BUCKET_ORDER,
  expectedCoOccurrence,
  expectedFrequency,
  frequencyRatio,
  frequencyStatsSchema,
  orderBuckets,
  patternStatsSchema,
  SUM_BUCKET_ORDER,
  type CombinationAnalysis,
  type CompanionStats,
  type FrequencyStats,
  type PatternStats,
} from "./schema";

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

describe("합계 구간 순서", () => {
  it("백엔드의 문자열 정렬 순서를 표시 순서로 바로잡는다", () => {
    // 백엔드는 bucketKey 오름차순으로 보낸다 — 문자열 정렬이라 "111-155"가 "21-65"보다
    // 앞선다. 응답 순서를 그대로 그리면 구간이 뒤죽박죽으로 나온다(§23.4 불변식).
    const asBackendSends = [
      { bucketKey: "111-155", count: 400 },
      { bucketKey: "156-200", count: 250 },
      { bucketKey: "201-255", count: 30 },
      { bucketKey: "21-65", count: 20 },
      { bucketKey: "66-110", count: 300 },
    ];

    expect(orderBuckets(asBackendSends, SUM_BUCKET_ORDER).map((b) => b.bucketKey)).toEqual([
      "21-65",
      "66-110",
      "111-155",
      "156-200",
      "201-255",
    ]);
  });

  it("개수 버킷도 숫자 순서로 정렬한다", () => {
    const shuffled = [
      { bucketKey: "6", count: 1 },
      { bucketKey: "0", count: 2 },
      { bucketKey: "3", count: 3 },
    ];

    expect(orderBuckets(shuffled, COUNT_BUCKET_ORDER).map((b) => b.bucketKey)).toEqual([
      "0",
      "3",
      "6",
    ]);
  });

  it("순서 목록에 없는 키를 버리지 않고 뒤에 붙인다", () => {
    // 백엔드가 구간을 늘렸을 때 그 값이 화면에서 조용히 사라지는 편이 더 나쁘다.
    const withUnknown = [
      { bucketKey: "256-300", count: 1 },
      { bucketKey: "21-65", count: 2 },
    ];

    expect(orderBuckets(withUnknown, SUM_BUCKET_ORDER).map((b) => b.bucketKey)).toEqual([
      "21-65",
      "256-300",
    ]);
  });

  it("원본 배열을 뒤집지 않는다", () => {
    const original = [
      { bucketKey: "6", count: 1 },
      { bucketKey: "0", count: 2 },
    ];
    orderBuckets(original, COUNT_BUCKET_ORDER);
    expect(original.map((b) => b.bucketKey)).toEqual(["6", "0"]);
  });
});

describe("패턴 통계 스키마", () => {
  const VALID = {
    totalRounds: 1150,
    oddCounts: [{ bucketKey: "3", count: 400 }],
    highCounts: [{ bucketKey: "3", count: 380 }],
    sumBuckets: [{ bucketKey: "111-155", count: 500 }],
  };

  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(patternStatsSchema, VALID).success).toBe(true);
  });

  it("버킷 묶음이 통째로 빠진 응답을 막는다", () => {
    const { sumBuckets: _omitted, ...withoutSum } = VALID;
    expect(v.safeParse(patternStatsSchema, withoutSum).success).toBe(false);
  });

  it("비율의 분모를 버킷 합으로 구한다", () => {
    expect(bucketTotal(VALID.oddCounts)).toBe(400);
    expect(bucketTotal([])).toBe(0);
  });
});

describe("쌍당 평균 동반 출현", () => {
  it("회차마다 생기는 15개 쌍을 990쌍이 나눠 갖는 비율로 계산한다", () => {
    // 이 기준선이 없으면 상위 쌍이 "특별히 잘 나오는 쌍"으로 오해된다(§23.5).
    expect(expectedCoOccurrence(990)).toBeCloseTo(15);
    expect(expectedCoOccurrence(1150)).toBeCloseTo(17.42, 2);
  });

  it("집계 회차가 0이면 배율이 null이다", () => {
    expect(coOccurrenceRatio(0, 0)).toBeNull();
  });

  it("평균 대비 배율을 돌려준다", () => {
    expect(coOccurrenceRatio(30, 990)).toBeCloseTo(2);
  });
});

describe("동반 출현 스키마", () => {
  const VALID = {
    totalRounds: 1150,
    topPairs: [{ ballA: 1, ballB: 12, coCount: 38 }],
  };

  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(companionStatsSchema, VALID).success).toBe(true);
  });

  it("번호 범위를 벗어난 쌍을 막는다", () => {
    const result = v.safeParse(companionStatsSchema, {
      ...VALID,
      topPairs: [{ ballA: 0, ballB: 12, coCount: 1 }],
    });
    expect(result.success).toBe(false);
  });
});

describe("조합 분석 스키마", () => {
  const VALID = {
    numbers: [1, 8, 17, 24, 33, 41],
    oddCount: 3,
    evenCount: 3,
    lowCount: 3,
    highCount: 3,
    sumOfNumbers: 124,
    sumBucket: "111-155",
    consecutivePairCount: 0,
    rangeDistribution: [{ range: "1-10", count: 2 }],
    wonFirstPrize: false,
    firstPrizeHistory: [],
  };

  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(analysisSchema, VALID).success).toBe(true);
  });
});

/** 생성 타입과의 정합성 — M-07(improvement_codex.md), improvement_fe.md §8.4 */
type GeneratedAnalysis = components["schemas"]["AnalysisResponse"];
const _analysisTypesMatch: CombinationAnalysis extends GeneratedAnalysis ? true : never = true;
void _analysisTypesMatch;

type GeneratedPatternStats = components["schemas"]["PatternStatsResponse"];
const _patternTypesMatch: PatternStats extends GeneratedPatternStats ? true : never = true;
void _patternTypesMatch;

type GeneratedFrequencyStats = components["schemas"]["FrequencyStatsResponse"];
const _frequencyTypesMatch: FrequencyStats extends GeneratedFrequencyStats ? true : never = true;
void _frequencyTypesMatch;

type GeneratedCompanionStats = components["schemas"]["CompanionStatsResponse"];
const _companionTypesMatch: CompanionStats extends GeneratedCompanionStats ? true : never = true;
void _companionTypesMatch;
