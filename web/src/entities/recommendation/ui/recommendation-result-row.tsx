import type { ReactNode } from "react";

import { LottoBallSet } from "../../round/ui/lotto-ball";
import { EXPLANATION_LABELS, type ExplanationCode } from "../schema";

import styles from "./recommendation-result-row.module.css";

/**
 * 추천 결과 한 줄 — improvement_fe_codex.md §12.2, §12.11
 *
 * 세로로 긴 카드 대신 "한 조합 = 한 행"이다. 왼쪽 라벨(`추천 1`), 가운데 6개 볼을
 * 좌→우 한 행, 오른쪽에 호출부가 주는 액션(저장 버튼 등)을 둔다. `/recommend`(방금
 * 만든 조합)와 `/recommend/history`(저장된 세트 안 조합들)가 둘 다 이 컴포넌트를
 * 쓴다 — 두 화면에서 "조합 하나"가 다르게 안 보이면 같은 데이터가 다른 화면으로
 * 보인다는 신뢰가 깨진다.
 */
export function RecommendationResultRow({
  index,
  numbers,
  explanationCodes = [],
  action,
}: {
  /** 1부터 시작하는 표시 번호(`추천 1`, `추천 2`, ...). */
  index: number;
  numbers: readonly number[];
  explanationCodes?: readonly ExplanationCode[];
  action?: ReactNode;
}) {
  return (
    <div className={styles.row}>
      <div className={styles.main}>
        <span className={styles.label}>추천 {index}</span>
        <LottoBallSet numbers={numbers} />
        {action !== undefined && <div className={styles.action}>{action}</div>}
      </div>

      {explanationCodes.length > 0 && (
        <ul className={styles.explanations}>
          {explanationCodes.map((code) => (
            <li key={code}>{EXPLANATION_LABELS[code]}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
