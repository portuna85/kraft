import { useEffect, useRef, useState } from "react";
import { createComment, deleteComment, fetchCommunityComments } from "@/lib/community-client";
import { BrowserApiError } from "@/lib/browser-api";
import type { CommunityComment } from "@/lib/community-api";
import { useCommunitySession } from "@/lib/community-session-provider";
import { useBlockedUserIds } from "@/features/community/blocked-users-context";
import { revalidateCommunityPost } from "@/lib/community-revalidate";

/**
 * 댓글 목록 조회/페이징/작성/삭제 상태를 들고 있는 훅. 컴포넌트는 렌더만 담당한다.
 */
export function useCommentSection(postId: number) {
  const [topLevel, setTopLevel] = useState<CommunityComment[]>([]);
  const [totalTopLevelComments, setTotalTopLevelComments] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const { session } = useCommunitySession();
  // C-1: 차단은 저장·조회만 될 뿐 렌더링에 적용되지 않았다 — 여기서 걸러낸다.
  const { isBlocked } = useBlockedUserIds();
  const [content, setContent] = useState("");
  const [replyTo, setReplyTo] = useState<{ id: number; authorNickname: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // FE-003: 삭제 확인 대상. null이면 다이얼로그가 닫힌 상태다.
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
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
          fetchError instanceof BrowserApiError ? fetchError.message : "댓글을 불러오지 못했습니다."
        );
      })
      .finally(() => {
        if (seq !== fetchSeqRef.current) return;
        setLoading(false);
      });
  };

  // F-04: loadComments는 매 렌더 새로 만들어지는 클로저라 deps에 넣으면 그 자체가
  // 매번 "바뀐 의존성"이 되어 무한 재조회 루프가 된다. 오직 postId가 바뀔 때만
  // 다시 불러오면 되므로 postId만 감시한다(회귀 테스트: community-comment-section.test.tsx
  // "postId가 바뀌지 않으면 리렌더링돼도 댓글을 다시 불러오지 않는다").
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
      const created = await createComment(postId, content.trim(), replyTo?.id ?? null);
      setContent("");
      setReplyTo(null);
      loadComments(created.targetPage ?? 0);
      // 목록 페이지의 commentCount는 30초 ISR 캐시라 댓글 작성 직후에도 그 창 동안
      // 낡은 값을 보여줄 수 있다 — 게시글 수정·삭제와 같은 패턴으로 즉시 무효화한다.
      // 실패해도 댓글 작성 자체는 이미 성공했으므로 사용자에게 오류로 보이지 않게 한다.
      void revalidateCommunityPost(postId).catch(() => {});
    } catch {
      setError("댓글 작성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  // FE-003: window.confirm 대신 공통 확인 다이얼로그를 쓴다. 어떤 댓글을 지우는지
  // id로 들고 있다가 확인 시점에 실행한다.
  const confirmDelete = async () => {
    if (pendingDeleteId === null) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteComment(pendingDeleteId);
      setPendingDeleteId(null);
      loadComments(page);
      void revalidateCommunityPost(postId).catch(() => {});
    } catch {
      setDeleteError("댓글 삭제에 실패했습니다.");
    } finally {
      setDeleting(false);
    }
  };

  // C-1: 차단된 작성자의 댓글은 걸러낸다. totalTopLevelComments(서버 집계)는 그대로 두고
  // 제목에 쓴다 — 화면에 보이는 개수와 어긋날 수 있는 것은 목록/피드와 같은 트레이드오프다.
  const visibleTopLevel = topLevel.filter((comment) => comment.ownerId === null || !isBlocked(comment.ownerId));

  return {
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
  };
}
