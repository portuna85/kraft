import type { RequiredApi } from "@/lib/api";
import type { components } from "@/lib/generated/api-types";
// KF-07: RecommendationItem/RecommendationSetSummary/Strategy/ExplanationCode는 커뮤니티
// 첨부 뷰(lib/community-api)도 똑같이 파생해 쓰는 중립 타입이라 lib/domain에 있다 —
// features가 lib을 참조하는 방향(정상)이지, lib이 features를 참조하면 안 된다. 이 파일에서
// 계속 쓰는 이름들이라 import와 재노출(export type) 둘 다 필요하다.
import type {
  ExplanationCode,
  RecommendationItem,
  RecommendationSetSummary,
  Strategy,
} from "@/lib/domain/recommendation";

export type { ExplanationCode, RecommendationItem, RecommendationSetSummary, Strategy };

// OpenAPI 생성 타입을 원시 계약으로 사용하고, springdoc이 표현하지 못하는 nullable 필드와
// 호환 응답의 optional 정책 메타데이터만 UI 경계에서 좁힌다.

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

/** 번호선택판의 3단 상태 — 한 번 누름: 고정, 두 번째: 제외, 세 번째: 해제. */
export type NumberPickState = "none" | "locked" | "excluded";

export const MAX_LOCKED_NUMBERS = 5;
export const MAX_COUNT = 10;
export const MIN_COUNT = 1;
