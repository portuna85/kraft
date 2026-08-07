import * as v from "valibot";

/**
 * 회차 계약 — improvement_fe.md §24.2(1)
 *
 * 손으로 쓴 타입가드였다면 `Array.isArray(numbers)`에서 멈췄을 것이다. 그러면 요소가
 * 문자열이거나 5개뿐인 응답이 화면 깊숙이 들어가 렌더 시점에 터진다(§6.2 H-3).
 * 아래 한 선언이 요소 타입·개수·범위까지 전부 덮는다.
 */
export const lottoNumberSchema = v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(45));

export const winningNumberSchema = v.object({
  round: v.pipe(v.number(), v.integer(), v.minValue(1)),
  /** ISO date (YYYY-MM-DD) */
  drawDate: v.string(),
  numbers: v.pipe(v.array(lottoNumberSchema), v.length(6)),
  bonusNumber: lottoNumberSchema,
  firstPrizeAmount: v.number(),
  secondPrize: v.number(),
  secondWinners: v.number(),
  totalSales: v.number(),
  firstAccumAmount: v.number(),
});

export type WinningNumber = v.InferOutput<typeof winningNumberSchema>;

export const roundFreshnessSchema = v.object({
  latestRound: v.pipe(v.number(), v.integer()),
  latestDrawDate: v.string(),
  fresh: v.boolean(),
  checkedAt: v.string(),
});

export type RoundFreshness = v.InferOutput<typeof roundFreshnessSchema>;
