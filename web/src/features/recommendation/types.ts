import type { RequiredApi } from "@/lib/api";
import type { components } from "@/lib/generated/api-types";

// OpenAPI 생성 타입을 원시 계약으로 사용하고, springdoc이 표현하지 못하는 nullable 필드와
// 호환 응답의 optional 정책 메타데이터만 UI 경계에서 좁힌다.

export type Strategy = "random" | "balanced" | "reduce_shared_winner_risk";

export type ExplanationCode = NonNullable<
  components["schemas"]["RecommendationItemView"]["explanationCodes"]
>[number];

type RecommendationItemContract = RequiredApi<
  components["schemas"]["RecommendationItemView"]
>;

export type RecommendationItem = Omit<
  RecommendationItemContract,
  "score" | "explanationCodes"
> & {
  score: number | null;
  explanationCodes: ExplanationCode[];
};

type RecommendationResponseContract = RequiredApi<
  components["schemas"]["RecommendNumbersResponse"]
>;

export type RecommendationResponse = Omit<
  RecommendationResponseContract,
  | "strategy"
  | "historicalExclusionApplied"
  | "exclusionPolicyVersion"
  | "setId"
  | "items"
  | "createdAt"
> & {
  strategy: Strategy;
  /** 구 버전 프록시/캐시 응답도 표시할 수 있도록 읽기에서는 optional로 둔다. */
  historicalExclusionApplied?: boolean;
  exclusionPolicyVersion?: string;
  setId: number | null;
  items: RecommendationItem[] | null;
  createdAt: string | null;
};

type RecommendationSetSummaryContract = RequiredApi<
  components["schemas"]["RecommendationSetSummary"]
>;

export type RecommendationSetSummary = Omit<
  RecommendationSetSummaryContract,
  "strategy" | "items"
> & {
  strategy: Strategy;
  items: RecommendationItem[];
};

/** 번호선택판의 3단 상태 — 한 번 누름: 고정, 두 번째: 제외, 세 번째: 해제. */
export type NumberPickState = "none" | "locked" | "excluded";

export const MAX_LOCKED_NUMBERS = 5;
export const MAX_COUNT = 10;
export const MIN_COUNT = 1;
