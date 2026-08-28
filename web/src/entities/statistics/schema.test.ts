import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import { analysisSchema, type CombinationAnalysis } from "./schema";

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

/** 생성 타입과의 정합성 — M-07, QA-FE-02(양방향) */
type GeneratedAnalysis = components["schemas"]["AnalysisResponse"];
const _analysisTypesMatch: CombinationAnalysis extends GeneratedAnalysis ? true : never = true;
void _analysisTypesMatch;
const _analysisReverseTypesMatch: GeneratedAnalysis extends CombinationAnalysis ? true : never =
  true;
void _analysisReverseTypesMatch;
