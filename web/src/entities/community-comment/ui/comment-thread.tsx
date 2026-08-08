import type { ReactNode } from "react";

import { formatDateTime } from "@/shared/lib/format";

import type { CommunityComment } from "../schema";

import styles from "./comment-thread.module.css";

/**
 * 2단 댓글 스레드 — improvement_fe.md §9.4, §23.10
 *
 * 중첩 `<ol>`로 그린다. 답글이 시각적 들여쓰기로만 구분되면 화면을 못 보는 사용자는
 * 어떤 댓글에 달린 답글인지 알 수 없다 — 목록 중첩이 그 관계를 구조로 전달한다.
 *
 * 액션(삭제·신고)은 이 컴포넌트가 만들지 않고 슬롯으로 받는다. entities는 도메인 표시만
 * 알고, 누가 무엇을 할 수 있는지는 feature의 판단이다(§7.4).
 */
export function CommentThread({
  comments,
  renderActions,
}: {
  comments: readonly CommunityComment[];
  renderActions?: (comment: CommunityComment) => ReactNode;
}) {
  return (
    <ol className={styles.thread}>
      {comments.map((comment) => (
        <li key={comment.id}>
          <CommentItem comment={comment} renderActions={renderActions} />
          {comment.replies.length > 0 && (
            <ol className={styles.replies}>
              {comment.replies.map((reply) => (
                <li key={reply.id}>
                  <CommentItem comment={reply} renderActions={renderActions} />
                </li>
              ))}
            </ol>
          )}
        </li>
      ))}
    </ol>
  );
}

/**
 * 삭제된 댓글은 사라지지 않고 자리를 지킨다.
 *
 * 지워 버리면 그 아래 답글들이 무엇에 대한 답인지 알 수 없게 된다 — 대화 맥락은 남긴
 * 채 내용과 작성자만 가린다(§25.6 tombstone).
 */
function CommentItem({
  comment,
  renderActions,
}: {
  comment: CommunityComment;
  renderActions?: (comment: CommunityComment) => ReactNode;
}) {
  return (
    <article className={`${styles.item} ${comment.deleted ? styles.deleted : ""}`}>
      <header className={styles.meta}>
        <span className={styles.author}>{comment.authorNickname}</span>
        <time dateTime={comment.createdAt}>{formatDateTime(comment.createdAt)}</time>
      </header>

      {/* 본문은 dangerouslySetInnerHTML 없이 줄 단위로 렌더한다(§20.1). */}
      <div className={styles.body}>
        {comment.content.split("\n").map((line, index) => (
          // 줄에는 안정적인 식별자가 없다. 목록이 재정렬되지 않으므로 인덱스로 충분하다.
          <p key={`${comment.id}-${index}`}>{line}</p>
        ))}
      </div>

      {/* 삭제된 댓글에는 액션을 붙이지 않는다 — 신고할 내용도 지울 것도 없다. */}
      {!comment.deleted && renderActions !== undefined && (
        <div className={styles.actions}>{renderActions(comment)}</div>
      )}
    </article>
  );
}
