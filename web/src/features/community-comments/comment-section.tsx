"use client";

import { useState } from "react";

import { createComment, deleteComment, fetchCommentPage } from "@/entities/community-comment/api";
import {
  COMMENT_MAX_LENGTH,
  isCommentSubmittable,
  type CommentPage,
  type CommunityComment,
} from "@/entities/community-comment/schema";
import { CommentThread } from "@/entities/community-comment/ui/comment-thread";
import { ReportDialog } from "@/entities/community-report/ui/report-dialog";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { TextArea } from "@/shared/ui/field";
import { EmptyState, InlineAlert } from "@/shared/ui/states";

import styles from "./comments.module.css";

/**
 * 댓글 — improvement_fe.md §23.10
 *
 * **첫 페이지는 서버가 이미 렌더해서 넘겨준다**(initialPage). 이 컴포넌트는 그것을 받아
 * 시작하므로, 초기 화면에 "댓글 0개"가 찍혔다가 채워지는 일이 없다. 더 보기·작성·삭제만
 * 브라우저가 맡는다.
 *
 * 답글은 상위 댓글 안에 들어 있고 페이징 집계에 포함되지 않는다(§25.6).
 */
export function CommentSection({
  postId,
  initialPage,
}: {
  postId: number;
  initialPage: CommentPage;
}) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const myUserId = session.session?.userId ?? null;

  const [comments, setComments] = useState<CommunityComment[]>(initialPage.topLevel);
  const [page, setPage] = useState(initialPage.page);
  const [totalPages, setTotalPages] = useState(initialPage.totalPages);
  const [total, setTotal] = useState(initialPage.totalTopLevelComments);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [replyTo, setReplyTo] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function loadMore() {
    setLoadingMore(true);
    setError(null);
    try {
      const next = await fetchCommentPage(postId, page + 1);
      setComments((current) => [...current, ...next.topLevel]);
      setPage(next.page);
      setTotalPages(next.totalPages);
      setTotal(next.totalTopLevelComments);
    } catch {
      setError("댓글을 더 불러오지 못했습니다.");
    } finally {
      setLoadingMore(false);
    }
  }

  /**
   * 작성 후 목록 전체를 다시 읽는다. 응답으로 온 댓글을 손으로 끼워 넣으면 답글이
   * 어느 상위 댓글에 속하는지, 몇 페이지에 있어야 하는지를 화면이 다시 계산해야 하고
   * 그 계산이 서버와 어긋나기 시작한다.
   */
  async function refreshFirstPage() {
    const fresh = await fetchCommentPage(postId, 0);
    setComments(fresh.topLevel);
    setPage(fresh.page);
    setTotalPages(fresh.totalPages);
    setTotal(fresh.totalTopLevelComments);
  }

  async function confirmDelete() {
    if (deleteTarget === null) return;
    setDeletePending(true);
    setDeleteError(null);
    try {
      await deleteComment(deleteTarget);
      await refreshFirstPage();
      setDeleteTarget(null);
    } catch {
      setDeleteError("댓글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setDeletePending(false);
    }
  }

  return (
    <section aria-labelledby="comments" className="stack">
      <h2 id="comments">댓글 {total}개</h2>

      {loggedIn ? (
        <CommentForm postId={postId} parentId={null} onDone={refreshFirstPage} label="댓글 작성" />
      ) : (
        <p className={styles.note}>로그인하면 댓글을 남길 수 있습니다.</p>
      )}

      {comments.length === 0 ? (
        <EmptyState
          reason="no-data"
          title="아직 댓글이 없습니다"
          description="이 글에 처음으로 댓글을 남겨 보세요."
        />
      ) : (
        <CommentThread
          comments={comments}
          renderActions={(comment) => (
            <>
              {loggedIn && comment.parentId === null && (
                <Button
                  variant="quiet"
                  onClick={() => setReplyTo(replyTo === comment.id ? null : comment.id)}
                  aria-expanded={replyTo === comment.id}
                >
                  답글
                </Button>
              )}
              {loggedIn && comment.ownerId === myUserId && (
                <Button variant="quiet" onClick={() => setDeleteTarget(comment.id)}>
                  삭제
                </Button>
              )}
              {loggedIn && comment.ownerId !== myUserId && (
                <ReportDialog targetType="COMMENT" targetId={comment.id} label="신고" />
              )}
              {replyTo === comment.id && (
                <CommentForm
                  postId={postId}
                  parentId={comment.id}
                  label="답글 작성"
                  onDone={async () => {
                    setReplyTo(null);
                    await refreshFirstPage();
                  }}
                />
              )}
            </>
          )}
        />
      )}

      {error !== null && <InlineAlert tone="danger">{error}</InlineAlert>}

      {page + 1 < totalPages &&
        (loadingMore ? (
          <Button variant="secondary" loading loadingLabel="불러오는 중">
            댓글 더 보기
          </Button>
        ) : (
          <Button variant="secondary" onClick={loadMore}>
            댓글 더 보기
          </Button>
        ))}

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        title="댓글을 삭제할까요?"
        variant="danger"
        description="삭제한 댓글은 되돌릴 수 없습니다. 달린 답글의 맥락을 위해 삭제 표시만 남습니다."
        confirmLabel="삭제"
        onConfirm={confirmDelete}
        pending={deletePending}
        errorMessage={deleteError}
      />
    </section>
  );
}

/** 작성 폼. 중복 제출은 제출 중 버튼을 잠가 막는다(§16.2). */
function CommentForm({
  postId,
  parentId,
  label,
  onDone,
}: {
  postId: number;
  parentId: number | null;
  label: string;
  onDone: () => Promise<void>;
}) {
  const [content, setContent] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submittable = isCommentSubmittable(content);

  async function submit() {
    if (!submittable || pending) return;
    setPending(true);
    setError(null);
    try {
      await createComment(postId, content.trim(), parentId);
      setContent("");
      await onDone();
    } catch {
      // 실패해도 입력은 지우지 않는다 — 쓴 것을 날리는 것이 가장 나쁜 실패다.
      setError("댓글을 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className={styles.form}>
      <TextArea
        label={label}
        rows={3}
        maxLength={COMMENT_MAX_LENGTH}
        value={content}
        onChange={(event) => setContent(event.target.value)}
        hint={`${content.length} / ${COMMENT_MAX_LENGTH}자`}
      />
      {error !== null && <InlineAlert tone="danger">{error}</InlineAlert>}
      {pending ? (
        <Button loading loadingLabel="등록 중">
          등록
        </Button>
      ) : (
        <Button onClick={submit} disabled={!submittable}>
          등록
        </Button>
      )}
    </div>
  );
}
