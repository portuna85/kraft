"use client";

import { EmptyState } from "@/ui/primitives/empty-state";
import { ConfirmDialog } from "@/ui/primitives/confirm-dialog";
import { CommentItem } from "./comment-item";
import { useCommentSection } from "./use-comment-section";

export function CommentSection({ postId }: { postId: number }) {
  const {
    session,
    isBlocked,
    visibleTopLevel,
    totalTopLevelComments,
    page,
    totalPages,
    content,
    setContent,
    replyTo,
    setReplyTo,
    submitting,
    error,
    loading,
    pendingDeleteId,
    setPendingDeleteId,
    deleting,
    deleteError,
    setDeleteError,
    loadComments,
    handleSubmit,
    confirmDelete,
  } = useCommentSection(postId);

  return (
    <section className="community-comment-section" aria-label="댓글">
      {/* 로딩 중에는 개수를 0으로 확정 표시하지 않는다 — 실제 값이 오기 전까지는
          숫자 없이 "댓글"만 보여준다 */}
      <h2 aria-busy={loading || undefined}>{loading ? "댓글" : `댓글 ${totalTopLevelComments}개`}</h2>
      {error && (
        <p role="alert" id="comment-section-error">
          {error}
        </p>
      )}

      {loading ? (
        // L-8: 평문 문단 대신 다른 화면(recommendation-history-client 등)과 같은
        // 스켈레톤 언어를 쓴다.
        <div aria-busy="true" aria-label="댓글을 불러오는 중">
          <span className="skeleton-line skeleton-body" />
          <span className="skeleton-line skeleton-body" />
        </div>
      ) : visibleTopLevel.length === 0 ? (
        // FE-009: 평문 문단 대신 다른 화면과 같은 빈 상태 표현을 쓴다.
        <EmptyState title="아직 댓글이 없습니다." description="첫 번째 댓글을 남겨 보세요." />
      ) : (
        <ul className="community-comment-list">
          {visibleTopLevel.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              isReply={false}
              loggedIn={Boolean(session?.loggedIn)}
              currentUserId={session?.loggedIn ? session.userId : null}
              isBlocked={isBlocked}
              onReply={setReplyTo}
              onRequestDelete={setPendingDeleteId}
            />
          ))}
        </ul>
      )}

      {totalPages > 1 && (
        <nav className="community-comment-pagination" aria-label="댓글 페이지">
          <button
            type="button"
            className="button secondary"
            disabled={page <= 0}
            onClick={() => loadComments(page - 1)}
          >
            이전
          </button>
          <span>
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            className="button secondary"
            disabled={page >= totalPages - 1}
            onClick={() => loadComments(page + 1)}
          >
            다음
          </button>
        </nav>
      )}

      {session?.loggedIn ? (
        <form onSubmit={handleSubmit} className="community-comment-form">
          {replyTo !== null && (
            <p className="community-comment-reply-target" role="status" aria-live="polite">
              <span>답글 작성 중</span>: {replyTo.authorNickname}님에게 답글을 작성합니다.
              <button type="button" className="button secondary" onClick={() => setReplyTo(null)}>
                취소
              </button>
            </p>
          )}
          <label htmlFor="comment-content">댓글 작성</label>
          <textarea
            id="comment-content"
            value={content}
            maxLength={1000}
            onChange={(event) => setContent(event.target.value)}
            required
            aria-invalid={error ? "true" : undefined}
            aria-describedby={error ? "comment-section-error" : undefined}
          />
          <button type="submit" disabled={submitting || !content.trim()}>
            {submitting ? "등록 중…" : "등록"}
          </button>
        </form>
      ) : (
        <p>댓글을 작성하려면 로그인이 필요합니다.</p>
      )}

      <ConfirmDialog
        open={pendingDeleteId !== null}
        title="댓글을 삭제할까요?"
        description="삭제한 댓글은 되돌릴 수 없습니다."
        confirmLabel="삭제"
        pending={deleting}
        errorMessage={deleteError ?? undefined}
        onConfirm={confirmDelete}
        onCancel={() => {
          setPendingDeleteId(null);
          setDeleteError(null);
        }}
      />
    </section>
  );
}
