// docs/improvement.md §9 추천 도메인 재설계와 대응하는 프런트 타입.
// 서버 응답 계약(recommendations/strategy/algorithmVersion/historyThroughRound)은
// 호환 유지 대상이라 절대 이름을 바꾸지 않는다 — setId/items/createdAt은 신규 필드.

export type Strategy = "random" | "balanced" | "reduce_shared_winner_risk";

export type ExplanationCode =
  | "ODD_EVEN_BALANCED"
  | "LOW_HIGH_BALANCED"
  | "SUM_IN_RANGE"
  | "CONSECUTIVE_PAIR_LIMITED"
  | "DECADE_SPREAD";

export type RecommendationItem = {
  position: number;
  numbers: number[];
  score: number | null;
  explanationCodes: ExplanationCode[];
};

export type RecommendationResponse = {
  recommendations: number[][];
  strategy: Strategy;
  algorithmVersion: string;
  historyThroughRound: number;
  setId: number | null;
  items: RecommendationItem[] | null;
  createdAt: string | null;
};

export type RecommendationSetSummary = {
  id: number;
  strategy: Strategy;
  algorithmVersion: string;
  historyThroughRound: number;
  lockedNumbers: number[];
  excludedNumbers: number[];
  createdAt: string;
  items: RecommendationItem[];
};

/** 번호선택판의 3단 상태 — 한 번 누름: 고정, 두 번째: 제외, 세 번째: 해제(문서 3.4절). */
export type NumberPickState = "none" | "locked" | "excluded";

export const MAX_LOCKED_NUMBERS = 5;
export const MAX_COUNT = 10;
export const MIN_COUNT = 1;
