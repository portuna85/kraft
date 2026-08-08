/**
 * 신고 계약 — improvement_fe.md §3.6 (열거값은 백엔드 계약이라 변경 금지)
 *
 * **자체 entity인 이유**: 게시글도 댓글도 신고 대상이다. 처음에는 신고 다이얼로그를
 * community-post feature에 뒀는데, community-comments feature가 그것을 쓰려다 계층
 * 규칙에 막혔다. 그게 "두 feature가 같은 것을 필요로 하면 entities로 올라가야 할
 * 개념"이라는 신호였다(§7.3).
 */
export const REPORT_REASONS = [
  "SPAM",
  "COMMERCIAL_PROMOTION",
  "HARASSMENT",
  "PERSONAL_INFO",
  "FAKE_WIN",
  "GAMBLING_GLORIFICATION",
  "OTHER",
] as const;

export type ReportReason = (typeof REPORT_REASONS)[number];

export const REPORT_REASON_LABELS: Record<ReportReason, string> = {
  SPAM: "스팸/도배",
  COMMERCIAL_PROMOTION: "상업적 홍보",
  HARASSMENT: "욕설/괴롭힘",
  PERSONAL_INFO: "개인정보 노출",
  FAKE_WIN: "허위 당첨 주장",
  GAMBLING_GLORIFICATION: "도박 미화",
  OTHER: "기타",
};

export const REPORT_TARGET_TYPES = ["POST", "COMMENT", "USER"] as const;
export type ReportTargetType = (typeof REPORT_TARGET_TYPES)[number];
