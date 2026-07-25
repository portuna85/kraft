"use client";

import { useEffect, useRef, useState } from "react";
import {
  createComment,
  deleteComment,
  fetchCommunityComments,
} from "@/lib/community-client";
import { BrowserApiError } from "@/lib/browser-api";
import type { CommunityComment } from "@/lib/community-api";
import { useCommunitySession } from "@/components/community/community-session-provider";

export function CommentSection({ postId }: { postId: number }) {
  const [topLevel, setTopLevel] = useState<CommunityComment[]>([]);
  const [totalTopLevelComments, setTotalTopLevelComments] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const { session } = useCommunitySession();
  const [content, setContent] = useState("");
  const [replyTo, setReplyTo] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // 목록 재조회 race 방지 — 오래된 응답이 최신 상태를 덮어쓰지 않도록 요청 시퀀스를 비교한다.
  const fetchSeqRef = useRef(0);

  const loadComments = (targetPage = 0) => {
    const seq = ++fetchSeqRef.current;
    fetchCommunityComments(postId, targetPage)
      .then((result) => {
        if (seq !== fetchSeqRef.current) return;
        setTopLevel(result.topLevel);
        setTotalTopLevelComments(result.totalTopLevelComments);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setError(null);
      })
      .catch((fetchError) => {
        if (seq !== fetchSeqRef.current) return;
        setError(
          fetchError instanceof BrowserApiError
            ? fetchError.message
            : "댓글을 불러오지 못했습니다."
        );
      })
      .finally(() => {
        if (seq !== fetchSeqRef.current) return;
        setLoading(false);
      });
  };

  useEffect(() => {
    loadComments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [postId]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting || !content.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const created = await createComment(postId, content.trim(), replyTo);
      setContent("");
      setReplyTo(null);
      loadComments(created.targetPage ?? 0);
    } catch {
      setError("댓글 작성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (commentId: number) => {
    if (!window.confirm("댓글을 삭제할까요?")) return;
    try {
      await deleteComment(commentId);
      loadComments(page);
    } catch {
      setError("댓글 삭제에 실패했습니다.");
    }
  };

  const renderComment = (comment: CommunityComment) => (
    <li key={comment.id} className="community-comment">
      <span className="community-comment-author">{comment.authorNickname}</span>
      <p>{comment.content}</p>
      {!comment.deleted && session?.loggedIn && (
        <div className="community-comment-actions">
          <button type="button" className="button secondary" onClick={() => setReplyTo(comment.id)}>
            답글
          </button>
          {session.userId === comment.ownerId && (
            <button type="button" className="button secondary" onClick={() => handleDelete(comment.id)}>
              삭제
            </button>
          )}
        </div>
      )}
      {comment.replies.length > 0 && (
        <ul className="community-comment-replies">
          {comment.replies.map((reply) => (
            <li key={reply.id} className="community-comment is-reply">
              <span className="community-comment-author">{reply.authorNickname}</span>
              <p>{reply.content}</p>
              {!reply.deleted && session?.loggedIn && session.userId === reply.ownerId && (
                <div className="community-comment-actions">
                  <button type="button" className="button secondary" onClick={() => handleDelete(reply.id)}>
                    삭제
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </li>
  );

  return (
    <section className="community-comment-section" aria-label="댓글">
      <h2>댓글 {totalTopLevelComments}개</h2>
      {error && (
        <p role="alert" id="comment-section-error">
          {error}
        </p>
      )}

      {loading ? (
        <p className="muted">불러오는 중…</p>
      ) : topLevel.length === 0 ? (
        <p className="muted">아직 댓글이 없습니다.</p>
      ) : (
        <ul className="community-comment-list">{topLevel.map(renderComment)}</ul>
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
            <p className="community-comment-reply-target">
              답글 작성 중
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
    </section>
  );
}
