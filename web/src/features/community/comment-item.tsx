import type { CommunityComment } from "@/lib/community-api";
import { ReportDialog } from "./report-dialog";

type CommentItemProps = {
  comment: CommunityComment;
  isReply: boolean;
  loggedIn: boolean;
  currentUserId: number | null;
  isBlocked: (ownerId: number) => boolean;
  onReply: (target: { id: number; authorNickname: string }) => void;
  onRequestDelete: (id: number) => void;
};

// M-6: 부모 댓글과 답글 렌더는 거의 동일한 구조(작성자/본문/작업 버튼)라 isReply로만
// 갈린다 — 답글에는 "답글" 버튼이 없고 자기 자신의 답글 목록도 재귀 렌더하지 않는다는
// 두 가지 차이만 조건부로 처리한다.
export function CommentItem({ comment, isReply, loggedIn, currentUserId, isBlocked, onReply, onRequestDelete }: CommentItemProps) {
  // C-1: 차단은 저장·조회만 될 뿐 렌더링에 적용되지 않았다 — 답글도 걸러낸다(답글은
  // 페이징 집계 대상이 아니므로 필터링이 페이지 카운트와 어긋나지 않는다).
  const visibleReplies = isReply
    ? []
    : comment.replies.filter((reply) => reply.ownerId === null || !isBlocked(reply.ownerId));

  return (
    <li key={comment.id} className={isReply ? "community-comment is-reply" : "community-comment"}>
      <span className="community-comment-author">{comment.authorNickname}</span>
      <p>{comment.content}</p>
      {!comment.deleted && loggedIn && (
        <div className="community-comment-actions">
          {!isReply && (
            <button
              type="button"
              className="button secondary"
              onClick={() => onReply({ id: comment.id, authorNickname: comment.authorNickname })}
            >
              답글
            </button>
          )}
          {currentUserId === comment.ownerId ? (
            <button type="button" className="button secondary" onClick={() => onRequestDelete(comment.id)}>
              삭제
            </button>
          ) : (
            <ReportDialog targetType="COMMENT" targetId={comment.id} />
          )}
        </div>
      )}
      {!isReply && visibleReplies.length > 0 && (
        <ul className="community-comment-replies">
          {visibleReplies.map((reply) => (
            <CommentItem
              key={reply.id}
              comment={reply}
              isReply
              loggedIn={loggedIn}
              currentUserId={currentUserId}
              isBlocked={isBlocked}
              onReply={onReply}
              onRequestDelete={onRequestDelete}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
