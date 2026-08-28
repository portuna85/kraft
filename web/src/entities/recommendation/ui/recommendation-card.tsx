import type { ReactNode } from "react";

import { formatDateTime } from "@/shared/lib/format";

import { STRATEGY_LABELS, type RecommendationItem, type Strategy } from "../schema";
import { RecommendationResultRow } from "./recommendation-result-row";

import styles from "./recommendation-card.module.css";

/**
 * 추천 조합 1세트
 *
 * 커뮤니티 글쓰기의 추천 첨부 선택기(`recommendation-attachment-picker.tsx`)와 게시글의
 * 추천 첨부 표시(`recommendation-attachment-view.tsx`)가 쓴다. 저장 슬롯은 선택적이다 —
 * 즉석 저장이 필요한 화면만 `renderSaveSlot`으로 항목별 액션을 끼워 넣는다.
 */
export function RecommendationCard({
  strategy,
  createdAt,
  historyThroughRound,
  items,
  renderSaveSlot,
}: {
  strategy: Strategy;
  createdAt: string;
  historyThroughRound: number;
  items: readonly RecommendationItem[];
  renderSaveSlot?: (item: RecommendationItem) => ReactNode;
}) {
  return (
    <div className={styles.card}>
      <p className="note">
        {STRATEGY_LABELS[strategy]} · {historyThroughRound}회까지 반영 ·{" "}
        <time dateTime={createdAt}>{formatDateTime(createdAt)}</time>
      </p>

      <ol className={styles.items}>
        {items.map((item, index) => (
          <li key={item.position}>
            <RecommendationResultRow
              index={index + 1}
              numbers={item.numbers}
              explanationCodes={item.explanationCodes}
              action={renderSaveSlot?.(item)}
            />
          </li>
        ))}
      </ol>
    </div>
  );
}
