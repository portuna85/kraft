"use client";

import { useEffect, useState } from "react";

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
 * 댓글
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

  // UX-03: 작성 후 목록만 새로고침하고 새 댓글로는 데려가지 않아 사용자가 직접
  // 스크롤해 찾아야 했다.
  const [highlightedId, setHighlightedId] = useState<number | null>(null);

  useEffect(() => {
    if (highlightedId === null) return;
    document
      .getElementById(`comment-${highlightedId}`)
      ?.scrollIntoView({ block: "center", behavior: "smooth" });
    const timer = setTimeout(() => setHighlightedId(null), 2000);
    return () => clearTimeout(timer);
  }, [highlightedId]);

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

  /**
   * UX-03: 새로고침 후 방금 쓴 댓글로 스크롤하고 잠깐 강조한다. targetPage가 0이
   * 아니면(상위 댓글이 50개를 넘어 새 댓글이 다음 페이지에 있는 경우) 이 새로고침이
   * 그 페이지까지 가져오지 않으므로 강조 대상이 DOM에 없어 스크롤은 조용히 아무
   * 일도 하지 않는다 — 답글은 항상 부모의 페이지에 실려 있어 이 문제가 없다.
   */
  async function handleCommentCreated(created: CommunityComment) {
    await refreshFirstPage();
    setHighlightedId(created.id);
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
      {/* I-33: "댓글 N개"가 답글을 세지 않아 헤더 숫자와 실제 스레드 개수가
          어긋나 보였다(§25.6 — 답글은 페이징 집계에 넣지 않는다). 집계 범위를
          라벨에 그대로 드러낸다. */}
      <h2 id="comments">원댓글 {total}개</h2>

      {loggedIn ? (
        <CommentForm postId={postId} parentId={null} onDone={handleCommentCreated} label="댓글 작성" />
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
          highlightedId={highlightedId}
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
                // I-25: CommentThread가 이 전체를 한 flex-wrap 행(.actions)에 담아
                // 답글 패널이 답글·삭제·신고 버튼과 나란한 flex 아이템이 됐었다 —
                // 폭 100%로 강제 줄바꿈시켜 버튼 행 아래 블록으로 내린다.
                <div className={styles.replyPanel}>
                  <CommentForm
                    postId={postId}
                    parentId={comment.id}
                    label="답글 작성"
                    autoFocus
                    onDone={async (created) => {
                      setReplyTo(null);
                      await handleCommentCreated(created);
                    }}
                  />
                </div>
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
  autoFocus,
}: {
  postId: number;
  parentId: number | null;
  label: string;
  onDone: (created: CommunityComment) => Promise<void>;
  // I-25: 답글 패널이 펼쳐질 때 포커스가 textarea로 옮겨가지 않았다 — aria-expanded는
  // 맞았지만 실제 포커스 이동이 없어 키보드 사용자가 직접 찾아 눌러야 했다. 상시
  // 마운트된 최상단 "댓글 작성" 폼에는 켜지 않는다(페이지 진입 시 포커스를 뺏으면 안 된다).
  autoFocus?: boolean;
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
      const created = await createComment(postId, content.trim(), parentId);
      setContent("");
      await onDone(created);
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
        autoFocus={autoFocus}
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
