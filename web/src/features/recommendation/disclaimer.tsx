// docs/improvement.md §7.2 — 추천 화면과 공유 카드에서 이 문구를 일관되게 유지한다.
export const PROBABILITY_DISCLAIMER_TEXT =
  "모든 유효한 로또 6/45 조합의 1등 당첨 확률은 동일합니다. 추천 전략은 번호를 선택하는 방식을 설명할 뿐 당첨 확률을 높이지 않습니다.";

export function ProbabilityDisclaimer() {
  return (
    <p className="muted" role="note">
      {PROBABILITY_DISCLAIMER_TEXT}
    </p>
  );
}
