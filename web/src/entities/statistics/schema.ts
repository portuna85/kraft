import * as v from "valibot";

import { lottoNumberSchema } from "../round/schema";

/**
 * 통계 계약 — improvement_fe.md §24.2(1)
 *
 * 같은 entity 안이므로 round 스키마를 상대 경로로 가져온다. 다른 entity를 @/ 별칭으로
 * 참조하면 계층 규칙에 걸린다 — 두 entity가 같은 값을 공유해야 한다면 그건 shared로
 * 내려가야 한다는 신호다. 여기서는 lottoNumberSchema가 로또 도메인 지식이라
 * round entity에 남는 것이 맞고, statistics가 그것을 읽는다.
 */
export const ballFrequencySchema = v.object({
  ballNumber: lottoNumberSchema,
  frequency: v.pipe(v.number(), v.integer(), v.minValue(0)),
  lastRound: v.pipe(v.number(), v.integer()),
});

export type BallFrequency = v.InferOutput<typeof ballFrequencySchema>;

export const firstPrizeHistorySchema = v.object({
  round: v.pipe(v.number(), v.integer()),
  drawDate: v.string(),
  firstPrizeAmount: v.number(),
});

export const rankedCombinationSchema = v.object({
  balls: v.array(ballFrequencySchema),
  wonFirstPrize: v.boolean(),
  firstPrizeHistory: v.array(firstPrizeHistorySchema),
});

export type RankedCombination = v.InferOutput<typeof rankedCombinationSchema>;

export const frequencyStatsSchema = v.object({
  totalRounds: v.pipe(v.number(), v.integer(), v.minValue(0)),
  frequencies: v.array(ballFrequencySchema),
  topSix: rankedCombinationSchema,
  bottomSix: rankedCombinationSchema,
});

export type FrequencyStats = v.InferOutput<typeof frequencyStatsSchema>;

/**
 * 무작위 기대값 — improvement_fe.md §6.3 M-6
 *
 * 통계 화면에 숫자만 나열하면 "봤는데 무엇을 알게 됐는지 모르는" 화면이 된다. 회차마다
 * 45개 중 6개가 뽑히므로 번호 하나의 기대 출현 횟수는 totalRounds × 6 / 45다. 이 값이
 * 있어야 "17번이 많이 나왔다"가 "기대보다 많다"로 읽힌다.
 */
export function expectedFrequency(totalRounds: number): number {
  return (totalRounds * 6) / 45;
}

/** 기대값 대비 배율. 기대값이 0이면 비교 자체가 불가능하므로 null이다. */
export function frequencyRatio(frequency: number, totalRounds: number): number | null {
  const expected = expectedFrequency(totalRounds);
  return expected === 0 ? null : frequency / expected;
}
