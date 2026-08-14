"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import { useState } from "react";

import { deletePost } from "@/entities/community-post/interactions";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { ApiError } from "@/shared/api/error";
import { ROUTES } from "@/shared/config/routes";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";

import styles from "./post-actions.module.css";

/**
 * 수정·삭제
 *
 * 노출 여부만 프런트가 판단한다. 실제 인가는 백엔드가 하며, 여기서 버튼을 감추는 것은
 * 보안이 아니라 UX다 — 누를 수 없는 버튼을 보여줄 이유가 없을 뿐이다.
 *
 * 삭제는 ConfirmDialog를 거친다(레거시 FE-003). `expectedVersion`을 함께 보내
 * 다른 탭에서 수정된 글을 모르고 지우는 일을 막는다.
 */
export function PostOwnerActions({
  postId,
  ownerId,
  version,
}: {
  postId: number;
  ownerId: number;
  version: number;
}) {
  const router = useRouter();
  const session = useSession();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isOwner = canQueryOwnerScope(session) && session.session?.userId === ownerId;
  if (!isOwner) return null;

  async function confirmDelete() {
    setPending(true);
    setError(null);
    try {
      await deletePost(postId, version);
      // 목록으로 돌아가되 캐시된 목록을 그대로 보여주지 않는다 — 방금 지운 글이 남아 있다.
      router.replace(ROUTES.community);
      router.refresh();
    } catch (cause) {
      setPending(false);
      setError(
        cause instanceof ApiError && cause.isConflict
          ? "다른 곳에서 이 글이 수정됐습니다. 새로고침해 최신 내용을 확인한 뒤 다시 시도해 주세요."
          : "글을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      );
    }
  }

  return (
    <div className={styles.bar}>
      <Link className={styles.editLink} href={ROUTES.communityPostEdit(postId)}>
        수정
      </Link>
      <Button variant="danger" onClick={() => setOpen(true)}>
        삭제
      </Button>

      <ConfirmDialog
        open={open}
        onClose={() => setOpen(false)}
        title="글을 삭제할까요?"
        variant="danger"
        description="삭제한 글은 되돌릴 수 없습니다. 달린 댓글의 맥락을 위해 삭제 표시만 남습니다."
        confirmLabel="삭제"
        onConfirm={confirmDelete}
        pending={pending}
        errorMessage={error}
      />
    </div>
  );
}
