import type { ExplanationCode } from "./types";

// 서버 ExplanationCode(com.kraft.recommend.ExplanationCode)와 1:1로 맞춰야 한다.
// 이 레코드는 모든 키를 필수로 요구하므로(Record<ExplanationCode, string>), 서버에
// 코드가 추가됐는데 여기 매핑을 빠뜨리면 타입 오류로 즉시 드러난다.
export const EXPLANATION_CODE_LABELS: Record<ExplanationCode, string> = {
  ODD_EVEN_BALANCED: "홀짝 번호 비율이 균형 잡혀 있어요",
  LOW_HIGH_BALANCED: "낮은 번호와 높은 번호 비율이 균형 잡혀 있어요",
  SUM_IN_RANGE: "번호 합계가 일반적인 범위 안에 있어요",
  CONSECUTIVE_PAIR_LIMITED: "연속된 번호가 거의 없어요",
  DECADE_SPREAD: "번호가 여러 구간에 고르게 퍼져 있어요",
};

export function explanationLabel(code: ExplanationCode): string {
  return EXPLANATION_CODE_LABELS[code];
}
