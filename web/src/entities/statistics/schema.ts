import * as v from "valibot";

import { lottoNumberSchema } from "../round/schema";

/**
 * 통계 계약
 *
 * 같은 entity 안이므로 round 스키마를 상대 경로로 가져온다. 다른 entity를 @/ 별칭으로
 * 참조하면 계층 규칙에 걸린다 — 두 entity가 같은 값을 공유해야 한다면 그건 shared로
 * 내려가야 한다는 신호다. 여기서는 lottoNumberSchema가 로또 도메인 지식이라
 * round entity에 남는 것이 맞고, statistics가 그것을 읽는다.
 */
export const firstPrizeHistorySchema = v.object({
  round: v.pipe(v.number(), v.integer()),
  drawDate: v.string(),
  firstPrizeAmount: v.number(),
});

/* ── 조합 분석 ─────────────────────────────────────────────────────────── */

export const rangeDistributionSchema = v.object({
  range: v.string(),
  count: v.pipe(v.number(), v.integer(), v.minValue(0)),
});

export const analysisSchema = v.object({
  numbers: v.array(lottoNumberSchema),
  oddCount: v.pipe(v.number(), v.integer(), v.minValue(0)),
  evenCount: v.pipe(v.number(), v.integer(), v.minValue(0)),
  lowCount: v.pipe(v.number(), v.integer(), v.minValue(0)),
  highCount: v.pipe(v.number(), v.integer(), v.minValue(0)),
  sumOfNumbers: v.pipe(v.number(), v.integer()),
  sumBucket: v.string(),
  consecutivePairCount: v.pipe(v.number(), v.integer(), v.minValue(0)),
  rangeDistribution: v.array(rangeDistributionSchema),
  wonFirstPrize: v.boolean(),
  firstPrizeHistory: v.array(firstPrizeHistorySchema),
});

export type CombinationAnalysis = v.InferOutput<typeof analysisSchema>;
