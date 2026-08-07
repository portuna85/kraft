import type { components } from "@/lib/generated/api-types";
import type { PostCategory } from "@/lib/community-api";

// M-17: 이전에는 Record<string, string>이라 백엔드가 사유를 추가/제거해도 타입체커가
// 못 잡았다 — 생성 타입(components["schemas"]["CreateReportRequest"]["reason"])에서
// 파생해 CATEGORY_LABELS와 동일하게 exhaustiveness를 보장한다.
export type ReportReason = components["schemas"]["CreateReportRequest"]["reason"];

export const CATEGORY_LABELS: Record<PostCategory, string> = {
  RECOMMENDATION_SHARE: "추천 공유",
  ROUND_ANALYSIS: "회차 분석",
  WIN_STORY: "당첨 후기",
  QUESTION: "질문",
  GENERAL: "자유",
};

export const CATEGORY_OPTIONS: readonly PostCategory[] = [
  "RECOMMENDATION_SHARE",
  "ROUND_ANALYSIS",
  "WIN_STORY",
  "QUESTION",
  "GENERAL",
];

export const REPORT_REASON_LABELS = {
  SPAM: "스팸",
  COMMERCIAL_PROMOTION: "상업적 홍보",
  HARASSMENT: "욕설·괴롭힘",
  PERSONAL_INFO: "개인정보 노출",
  FAKE_WIN: "허위 당첨",
  GAMBLING_GLORIFICATION: "도박 미화",
  OTHER: "기타",
} as const satisfies Record<ReportReason, string>;
