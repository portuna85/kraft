"use client";

import { useId, type ReactNode } from "react";

import { useDisclosure } from "@/shared/hooks/use-disclosure";
import { Badge } from "@/shared/ui/badge";

import { LottoBallSet } from "../../round/ui/lotto-ball";
import { EXPLANATION_CHIP_LABELS, EXPLANATION_LABELS, type ExplanationCode } from "../schema";

import styles from "./recommendation-result-row.module.css";

/**
 * 추천 결과 한 줄
 *
 * 세로로 긴 카드 대신 "한 조합 = 한 행"이다. 왼쪽 라벨(`추천 1`), 가운데 6개 볼을
 * 좌→우 한 행, 오른쪽에 호출부가 주는 액션(저장 버튼 등)을 둔다. `/recommend`(방금
 * 만든 조합)와 커뮤니티 글쓰기의 추천 첨부 선택기(저장된 세트 안 조합들)가 둘 다 이
 * 컴포넌트를 쓴다 — 두 화면에서 "조합 하나"가 다르게 안 보이면 같은 데이터가 다른
 * 화면으로 보인다는 신뢰가 깨진다.
 *
 * kraft-redesign-plan.md P0: 조합마다 반복되는 전체 문장 목록을 짧은 칩으로 줄이고,
 * 전체 문장은 "자세히" 펼침 뒤로 둔다. 칩은 그 조합에 실제로 매겨진
 * `explanationCodes`만 나타낸다 — 백엔드가 판정하지 않은 특성을 새로 주장하지 않는다.
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
  const { isOpen, toggle } = useDisclosure();
  // 같은 페이지에 여러 세트(RecommendationCard)가 렌더되면 index가 세트마다
  // 1부터 다시 시작해 겹친다 — id는 index가 아니라 컴포넌트 인스턴스 단위로
  // 고유해야 하는 useId()로 만든다.
  const detailId = useId();

  return (
    <div className={styles.row}>
      <div className={styles.main}>
        <span className={styles.label}>추천 {index}</span>
        <LottoBallSet numbers={numbers} />
        {action !== undefined && <div className={styles.action}>{action}</div>}
      </div>

      {explanationCodes.length > 0 && (
        <>
          <div className={styles.chips}>
            {explanationCodes.map((code) => (
              <Badge key={code} tone="neutral" size="sm" label={EXPLANATION_CHIP_LABELS[code]} />
            ))}
            <button
              type="button"
              className={styles.detailToggle}
              aria-expanded={isOpen}
              aria-controls={detailId}
              onClick={toggle}
            >
              {isOpen ? "간단히" : "자세히"}
            </button>
          </div>

          <ul id={detailId} className={styles.explanations} hidden={!isOpen}>
            {explanationCodes.map((code) => (
              <li key={code}>{EXPLANATION_LABELS[code]}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
