import * as v from "valibot";

/**
 * 저장 번호 계약
 *
 * **자체 entity인 이유**: 추천 스튜디오(생성 직후 저장)와 보관함(조회·대조·삭제)이 둘 다
 * 이 모델을 쓴다. 처음에는 entities/recommendation 안에 있었는데, 그러면 saved-library
 * feature가 추천 entity를 통해 저장 번호를 다루게 되어 소유가 흐려진다.
 *
 * numbers는 정확히 6개다. 레거시의 손으로 쓴 타입가드는 배열인지만 보고 요소 타입도
 * 개수도 검사하지 않았다(§6.2 H-3) — 그 자리가 이 한 줄로 덮인다.
 */
const lottoNumberSchema = v.pipe(v.number(), v.integer(), v.minValue(1), v.maxValue(45));

export const savedNumberSchema = v.object({
  id: v.number(),
  numbers: v.pipe(v.array(lottoNumberSchema), v.length(6)),
  label: v.nullable(v.string()),
  source: v.string(),
  createdAt: v.string(),
});

export type SavedNumber = v.InferOutput<typeof savedNumberSchema>;

export const savedNumberListSchema = v.array(savedNumberSchema);

/** 저장 응답. created가 false면 이미 있던 조합이다 — 오류가 아니라 안내 대상이다. */
export const saveNumberResultSchema = v.object({
  savedNumber: savedNumberSchema,
  created: v.boolean(),
});

export type SaveNumberResult = v.InferOutput<typeof saveNumberResultSchema>;

/**
 * 회차 대조 결과.
 *
 * prizeTier는 백엔드 LottoRank가 만든 **이미 한국어인** 문자열이다("1등"…"5등"·"낙첨").
 * 그대로 표시한다 — 프런트에서 등수를 다시 계산하면 두 곳의 규칙이 갈라지고, 코드로
 * 받아 다시 번역하면 백엔드가 등급을 늘렸을 때 빈 칸이 된다.
 */
export const savedNumberMatchSchema = v.object({
  savedNumber: savedNumberSchema,
  round: v.pipe(v.number(), v.integer()),
  drawDate: v.string(),
  drawNumbers: v.array(lottoNumberSchema),
  bonusNumber: lottoNumberSchema,
  matchedCount: v.pipe(v.number(), v.integer(), v.minValue(0)),
  bonusMatch: v.boolean(),
  prizeTier: v.string(),
});

export type SavedNumberMatch = v.InferOutput<typeof savedNumberMatchSchema>;

export const savedNumberMatchListSchema = v.array(savedNumberMatchSchema);

/** 백엔드 LottoRank의 낙첨 값. 당첨 여부를 색이 아니라 이 값으로 가른다. */
export const NO_PRIZE_TIER = "낙첨";

export function isWinning(tier: string): boolean {
  return tier !== NO_PRIZE_TIER && tier !== "";
}
