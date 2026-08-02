"use client";

import { useRef, useState } from "react";
import { Dialog } from "@/ui/primitives/dialog";
import { blockUser, unblockUser } from "@/lib/community-client";
import { useCommunitySession } from "@/lib/community-session-provider";

export function BlockButton({ userId }: { userId: number }) {
  const { session } = useCommunitySession();
  const [blocked, setBlocked] = useState(false);
  const [message, setMessage] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);

  // FE-057: 차단은 되돌리기 어려운 작업인데 클릭 즉시 실행됐고, 이 화면에 해제 경로가
  // 없었다. unblockUser()는 community-client에 이미 있었으나 어떤 화면도 호출하지 않았다.
  async function confirmBlock() {
    setPending(true);
    setMessage("");
    try {
      await blockUser(userId);
      setBlocked(true);
      setConfirmOpen(false);
    } catch {
      setMessage("처리하지 못했습니다. 잠시 후 다시 시도하세요.");
    } finally {
      setPending(false);
    }
  }

  async function handleUnblock() {
    setPending(true);
    setMessage("");
    try {
      await unblockUser(userId);
      setBlocked(false);
    } catch {
      setMessage("처리하지 못했습니다. 잠시 후 다시 시도하세요.");
    } finally {
      setPending(false);
    }
  }

  // 본인 글에는 차단 버튼을 보여주지 않는다 — 자기차단은 애초에 서버가 400으로 거부한다.
  if (!session?.loggedIn || session.userId === userId) {
    return null;
  }

  if (blocked) {
    return (
      <>
        <span role="status">차단했습니다.</span>
        <button type="button" onClick={handleUnblock} disabled={pending}>
          {pending ? "처리 중…" : "차단 해제"}
        </button>
        {message ? <span role="alert">{message}</span> : null}
      </>
    );
  }

  return (
    <>
      <button type="button" ref={triggerRef} onClick={() => setConfirmOpen(true)} disabled={pending}>
        차단
      </button>
      <Dialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        restoreFocusRef={triggerRef}
        titleId={`block-dialog-title-${userId}`}
        title="이 사용자를 차단할까요?"
      >
        <p>차단하면 이 사용자의 게시글과 댓글이 보이지 않습니다. 차단은 언제든 해제할 수 있습니다.</p>
        <div>
          <button type="button" onClick={() => setConfirmOpen(false)} disabled={pending}>
            취소
          </button>
          <button type="button" onClick={confirmBlock} disabled={pending}>
            {pending ? "처리 중…" : "차단"}
          </button>
        </div>
        {message ? <p role="alert">{message}</p> : null}
      </Dialog>
    </>
  );
}
