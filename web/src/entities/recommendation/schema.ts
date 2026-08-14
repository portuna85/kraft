import * as v from "valibot";

import { lottoNumberSchema } from "../round/schema";

/**
 * 추천 계약
 * 열거값은 백엔드 계약이라 임의로 늘리거나 줄이지 않는다(§3.6).
 */
export const STRATEGIES = ["random", "balanced", "reduce_shared_winner_risk"] as const;
export type Strategy = (typeof STRATEGIES)[number];

export const STRATEGY_LABELS: Record<Strategy, string> = {
  random: "완전 무작위",
  balanced: "균형 잡힌 조합",
  reduce_shared_winner_risk: "당첨금 분산 줄이기",
};

export const STRATEGY_DESCRIPTIONS: Record<Strategy, string> = {
  random: "1~45에서 조건 없이 뽑습니다. 통계를 전혀 반영하지 않는 기준선입니다.",
  balanced: "홀짝·고저·합계가 과거 당첨 조합의 흔한 범위에 들도록 고릅니다.",
  reduce_shared_winner_risk:
    "생일·연속번호처럼 많은 사람이 함께 고르는 형태를 피합니다. 당첨 확률이 아니라 당첨 시 나눠 갖는 인원을 줄이려는 선택입니다.",
};

/** 설명 코드 → 한국어 문구(§3.2). 코드를 그대로 노출하면 사용자에게 아무 뜻이 없다. */
export const EXPLANATION_LABELS = {
  ODD_EVEN_BALANCED: "홀짝이 고르게 섞였습니다",
  LOW_HIGH_BALANCED: "낮은 수와 높은 수가 고르게 섞였습니다",
  SUM_IN_RANGE: "번호 합이 과거 당첨 조합의 흔한 범위에 있습니다",
  CONSECUTIVE_PAIR_LIMITED: "연속된 번호가 과하지 않습니다",
  DECADE_SPREAD: "10번대 구간에 고르게 퍼져 있습니다",
} as const;

export type ExplanationCode = keyof typeof EXPLANATION_LABELS;

export const explanationCodeSchema = v.picklist(
  Object.keys(EXPLANATION_LABELS) as ExplanationCode[],
);

export const recommendationItemSchema = v.object({
  position: v.pipe(v.number(), v.integer()),
  numbers: v.pipe(v.array(lottoNumberSchema), v.length(6)),
  score: v.nullable(v.number()),
  explanationCodes: v.array(explanationCodeSchema),
});

export type RecommendationItem = v.InferOutput<typeof recommendationItemSchema>;

export const recommendNumbersSchema = v.object({
  recommendations: v.array(v.pipe(v.array(lottoNumberSchema), v.length(6))),
  strategy: v.picklist(STRATEGIES),
  algorithmVersion: v.string(),
  historyThroughRound: v.pipe(v.number(), v.integer()),
  historicalExclusionApplied: v.boolean(),
  exclusionPolicyVersion: v.string(),
  setId: v.nullable(v.number()),
  items: v.nullable(v.array(recommendationItemSchema)),
  createdAt: v.nullable(v.string()),
});

export type RecommendNumbers = v.InferOutput<typeof recommendNumbersSchema>;

/** 고정 번호 상한과 조합 개수 범위는 백엔드 제약과 일치해야 한다(§3.2). */
export const MAX_LOCKED_NUMBERS = 5;
export const MIN_COUNT = 1;
export const MAX_COUNT = 10;

/**
 * 추천 이력 1건
 *
 * 저장 시점에 만들어진 조합 묶음(전략·고정/제외 조건·조합 목록)을 그대로 보존한다.
 * `recommendationItemSchema`를 그대로 재사용한다 — 백엔드 `RecommendationItemView`와
 * `RecommendationSetSummary.items()`가 같은 모양을 쓴다.
 */
export const recommendationSetSchema = v.object({
  id: v.number(),
  strategy: v.picklist(STRATEGIES),
  algorithmVersion: v.string(),
  historyThroughRound: v.pipe(v.number(), v.integer()),
  exclusionPolicyVersion: v.string(),
  lockedNumbers: v.array(lottoNumberSchema),
  excludedNumbers: v.array(lottoNumberSchema),
  createdAt: v.string(),
  items: v.array(recommendationItemSchema),
});

export type RecommendationSet = v.InferOutput<typeof recommendationSetSchema>;

export const recommendationSetPageSchema = v.object({
  items: v.array(recommendationSetSchema),
  page: v.pipe(v.number(), v.integer()),
  size: v.pipe(v.number(), v.integer()),
  totalElements: v.pipe(v.number(), v.integer()),
  totalPages: v.pipe(v.number(), v.integer()),
});

export type RecommendationSetPage = v.InferOutput<typeof recommendationSetPageSchema>;
