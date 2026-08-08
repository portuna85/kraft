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

/* ── 패턴 통계 ─────────────────────────────────────────────────────────── */

export const patternBucketSchema = v.object({
  bucketKey: v.string(),
  count: v.pipe(v.number(), v.integer(), v.minValue(0)),
});

export type PatternBucket = v.InferOutput<typeof patternBucketSchema>;

export const patternStatsSchema = v.object({
  totalRounds: v.pipe(v.number(), v.integer(), v.minValue(0)),
  oddCounts: v.array(patternBucketSchema),
  highCounts: v.array(patternBucketSchema),
  sumBuckets: v.array(patternBucketSchema),
});

export type PatternStats = v.InferOutput<typeof patternStatsSchema>;

/**
 * 합계 구간 표시 순서 — improvement_fe.md §23.4 불변식
 *
 * 백엔드는 bucketKey **문자열 오름차순**으로 정렬해 보낸다(`findByStatTypeOrderByBucketKeyAsc`).
 * 문자열 정렬에서는 "111-155"가 "21-65"보다 앞선다 — 즉 응답 순서를 그대로 그리면
 * 구간이 뒤죽박죽으로 나온다. 표시 순서는 여기서 정한다.
 */
export const SUM_BUCKET_ORDER = ["21-65", "66-110", "111-155", "156-200", "201-255"] as const;

/** 홀수·고수 개수 버킷은 0~6개다. 키가 숫자 문자열이라 이쪽도 문자열 정렬이 위험하다. */
export const COUNT_BUCKET_ORDER = ["0", "1", "2", "3", "4", "5", "6"] as const;

/**
 * 지정한 순서로 버킷을 정렬한다. 순서 목록에 없는 키는 뒤에 원래 순서로 붙인다 —
 * 백엔드가 구간을 늘렸을 때 그 값이 화면에서 조용히 사라지는 것보다는 낫다.
 */
export function orderBuckets(
  buckets: readonly PatternBucket[],
  order: readonly string[],
): PatternBucket[] {
  const rank = new Map(order.map((key, index) => [key, index]));
  return [...buckets].sort(
    (a, b) => (rank.get(a.bucketKey) ?? order.length) - (rank.get(b.bucketKey) ?? order.length),
  );
}

/** 버킷 묶음의 총합. 비율 계산의 분모이며, 0이면 비율 자체가 정의되지 않는다. */
export function bucketTotal(buckets: readonly PatternBucket[]): number {
  return buckets.reduce((sum, bucket) => sum + bucket.count, 0);
}

/* ── 동반 출현 통계 ─────────────────────────────────────────────────────── */

export const companionPairSchema = v.object({
  ballA: lottoNumberSchema,
  ballB: lottoNumberSchema,
  coCount: v.pipe(v.number(), v.integer(), v.minValue(0)),
});

export type CompanionPair = v.InferOutput<typeof companionPairSchema>;

export const companionStatsSchema = v.object({
  totalRounds: v.pipe(v.number(), v.integer(), v.minValue(0)),
  topPairs: v.array(companionPairSchema),
});

export type CompanionStats = v.InferOutput<typeof companionStatsSchema>;

/** 45개 번호로 만들 수 있는 쌍의 수 (45C2). 평균 기준선의 분모다. */
export const TOTAL_PAIR_COUNT = 990;

/**
 * 쌍당 평균 동반 출현 — improvement_fe.md §23.5 불변식
 *
 * 회차마다 6개가 뽑히므로 한 회차가 만드는 쌍은 6C2 = 15개다. 이것을 990쌍이 나눠
 * 가지므로 쌍 하나의 평균 동반 출현은 totalRounds × 15 / 990이다. 이 기준선이 없으면
 * 상위 쌍의 횟수가 "특별히 잘 나오는 쌍"으로 오해된다.
 */
export function expectedCoOccurrence(totalRounds: number): number {
  return (totalRounds * 15) / TOTAL_PAIR_COUNT;
}

/** 평균 대비 배율. 평균이 0이면 비교가 불가능하므로 null이다. */
export function coOccurrenceRatio(coCount: number, totalRounds: number): number | null {
  const expected = expectedCoOccurrence(totalRounds);
  return expected === 0 ? null : coCount / expected;
}
