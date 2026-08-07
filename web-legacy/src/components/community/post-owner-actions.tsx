"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { deletePost } from "@/lib/community-client";
import { revalidateCommunityPost } from "@/lib/community-revalidate";
import { useCommunitySession } from "@/lib/community-session-provider";
import { ConfirmDialog } from "@/ui/primitives/confirm-dialog";

// 소유권 판정은 서버 응답에 canEdit 같은 파생 필드로 섞지 않고, 클라이언트가
// 세션 엔드포인트의 로그인 사용자 ID와 게시글 ownerId를 직접 대조한다.
export function PostOwnerActions({
  postId,
  ownerId,
  version,
}: {
  postId: number;
  ownerId: number;
  version: number;
}) {
  const { session } = useCommunitySession();
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // FE-003: window.confirm 대신 공통 확인 다이얼로그를 쓴다.
  const [confirmOpen, setConfirmOpen] = useState(false);
  const router = useRouter();
  const isOwner = Boolean(session?.loggedIn && session.userId === ownerId);

  if (!isOwner) {
    return null;
  }

  async function confirmDelete() {
    setDeleting(true);
    setError(null);
    try {
      await deletePost(postId, version);
      await revalidateCommunityPost(postId);
      setConfirmOpen(false);
      router.push("/community");
      router.refresh();
    } catch {
      setError("다른 곳에서 먼저 수정·삭제되었습니다. 새로고침 후 다시 시도하세요.");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="community-post-owner-actions">
      <Link href={`/community/posts/${postId}/edit`}>수정</Link>
      <button type="button" disabled={deleting} onClick={() => setConfirmOpen(true)}>
        삭제
      </button>
      <ConfirmDialog
        open={confirmOpen}
        title="게시글을 삭제할까요?"
        description="삭제한 게시글과 달린 댓글은 되돌릴 수 없습니다."
        confirmLabel="삭제"
        pending={deleting}
        errorMessage={error ?? undefined}
        onConfirm={confirmDelete}
        onCancel={() => {
          setConfirmOpen(false);
          setError(null);
        }}
      />
    </div>
  );
}
