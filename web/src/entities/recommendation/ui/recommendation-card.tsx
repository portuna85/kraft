import type { ReactNode } from "react";

import { formatDateTime } from "@/shared/lib/format";

import { STRATEGY_LABELS, type RecommendationItem, type Strategy } from "../schema";
import { RecommendationResultRow } from "./recommendation-result-row";

import styles from "./recommendation-card.module.css";

/**
 * 추천 조합 1세트
 *
 * `/recommend/history`에서 쓴다. 저장 슬롯은 선택적이다 — 이력의 조합은 만든 시점에
 * 이미 저장돼 있어 별도 저장 액션이 없다(레거시 참조: recommendation-history-client.tsx
 * 는 조회·삭제만 제공한다). 다른 화면이 재사용하며 즉석 저장이 필요해지면
 * `renderSaveSlot`으로 항목별 액션을 끼워 넣는다.
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
