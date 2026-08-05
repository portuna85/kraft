import type { components } from "@/lib/generated/api-types";

// KF-07: features/recommendation(추천 생성 플로우)와 lib/community-api(커뮤니티 첨부 뷰)가
// 같은 백엔드 스키마(RecommendationItemView/RecommendationSetSummary)를 각자 따로
// 파생시키고 있었다 — community-client.ts가 그중 features 쪽을 직접 import해 lib→features
// 역방향 의존까지 생겼다. 이 중립 위치(lib/domain)에 한 번만 파생시키고 양쪽이 이걸 쓴다.

export type ExplanationCode = NonNullable<
  components["schemas"]["RecommendationItemView"]["explanationCodes"]
>[number];

type RecommendationItemContract = components["schemas"]["RecommendationItemView"];

export type RecommendationItem = Omit<
  RecommendationItemContract,
  "score" | "explanationCodes"
> & {
  score: number | null;
  explanationCodes: ExplanationCode[];
};

// M-17: 예전에는 이 유니온을 손으로 따로 선언해 백엔드 계약과 별개로 관리했다 —
// 백엔드가 strategy를 enum으로 승격한 뒤로는 생성 타입에서 그대로 파생한다.
export type Strategy = components["schemas"]["RecommendNumbersResponse"]["strategy"];

// L-1: 생성·이력·커뮤니티 첨부·전략 선택 화면 4곳이 이 라벨을 각자 손으로 베껴 쓰고
// 있었다 — 전략 이름이 바뀌면 화면마다 다르게 렌더될 수 있었다. satisfies로
// Strategy 유니온에 대한 exhaustiveness도 컴파일 타임에 보장한다.
export const STRATEGY_LABELS = {
  random: "무작위",
  balanced: "균형 조합",
  reduce_shared_winner_risk: "공동 당첨 위험 완화",
} as const satisfies Record<Strategy, string>;

// /info/methodology의 "균형 조합" 설명과 같은 용어를 쓴다 — 두 곳의 표현이
// 어긋나면 안 된다는 요구에 맞춰 문구를 여기서만 관리한다.
export const STRATEGY_DESCRIPTIONS = {
  random: "조건 없이 1~45에서 임의로 6개를 뽑습니다.",
  balanced: "홀짝·고저·합계·구간 분포를 고르게 맞춥니다.",
  reduce_shared_winner_risk: "많은 사람이 고르는 패턴을 피해 조합을 고릅니다.",
} as const satisfies Record<Strategy, string>;

type RecommendationSetSummaryContract = components["schemas"]["RecommendationSetSummary"];

export type RecommendationSetSummary = Omit<
  RecommendationSetSummaryContract,
  "strategy" | "items"
> & {
  strategy: Strategy;
  items: RecommendationItem[];
};
