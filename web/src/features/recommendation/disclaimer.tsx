// 추천 화면과 공유 카드에서 확률·역대 당첨 조합 제외 정책을 동일하게 안내한다.
export const PROBABILITY_DISCLAIMER_TEXT =
  "모든 유효한 로또 6/45 조합의 1등 당첨 확률은 동일합니다. 추천 전략은 번호를 선택하는 방식을 설명할 뿐 당첨 확률을 높이지 않습니다.";

export const HISTORY_DISCLAIMER_TEXT =
  "추천은 당첨을 보장하지 않습니다. 과거 출현 빈도와 패턴은 미래 추첨 결과를 예측하지 않으며, 통계 정보는 탐색과 오락을 위한 참고 자료입니다.";

export const HISTORICAL_EXCLUSION_TEXT = "역대 1등 당첨 번호 조합은 모든 추천 전략에서 항상 제외됩니다.";

export function ProbabilityDisclaimer() {
  return (
    <>
      <p className="recommend-disclaimer muted" role="note">{PROBABILITY_DISCLAIMER_TEXT}</p>
      <p className="recommend-disclaimer muted" role="note">{HISTORY_DISCLAIMER_TEXT}</p>
      <p className="recommend-disclaimer muted" role="note">{HISTORICAL_EXCLUSION_TEXT}</p>
    </>
  );
}
