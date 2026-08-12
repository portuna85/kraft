import type { RecommendationAttachment } from "@/entities/community-post/schema";
import { RecommendationResultRow } from "@/entities/recommendation/ui/recommendation-result-row";
import { STRATEGY_LABELS } from "@/entities/recommendation/schema";

import styles from "./recommendation-attachment-view.module.css";

/**
 * H-03: 게시글에 첨부된 추천 세트의 불변 스냅숏을 읽기 전용으로 보여준다.
 *
 * `RecommendationAttachmentView`(백엔드)는 작성 시점의 전략·버전·제외정책을 그대로
 * 보존한다 — 원본 추천 세트가 나중에 삭제돼도 이 스냅숏은 바뀌지 않는다. 그래서
 * `entities/recommendation`의 이력 카드(`RecommendationCard`)를 그대로 재사용하지
 * 않고, algorithmVersion·exclusionPolicyVersion까지 함께 보여주는 별도 뷰를 둔다.
 */
export function RecommendationAttachmentView({
  attachment,
}: {
  attachment: RecommendationAttachment;
}) {
  return (
    <section className={styles.attachment} aria-label="첨부된 추천 세트">
      <p className={styles.heading}>첨부된 추천 세트</p>
      <p className={styles.meta}>
        {STRATEGY_LABELS[attachment.strategy]} · {attachment.historyThroughRound}회까지 반영 ·
        알고리즘 {attachment.algorithmVersion} · 제외 정책 {attachment.exclusionPolicyVersion}
      </p>

      <ol className={styles.items}>
        {attachment.items.map((item, index) => (
          <li key={item.position}>
            <RecommendationResultRow
              index={index + 1}
              numbers={item.numbers}
              explanationCodes={item.explanationCodes}
            />
          </li>
        ))}
      </ol>
    </section>
  );
}
