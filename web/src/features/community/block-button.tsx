"use client";

import { useState } from "react";
import { blockUser } from "@/lib/community-client";
import { useCommunitySession } from "@/lib/community-session-provider";

export function BlockButton({ userId }: { userId: number }) {
  const { session } = useCommunitySession();
  const [blocked, setBlocked] = useState(false);
  const [message, setMessage] = useState("");

  async function handleClick() {
    try {
      await blockUser(userId);
      setBlocked(true);
    } catch {
      setMessage("처리하지 못했습니다. 잠시 후 다시 시도하세요.");
    }
  }

  // 본인 글에는 차단 버튼을 보여주지 않는다 — 자기차단은 애초에 서버가 400으로 거부한다.
  if (!session?.loggedIn || session.userId === userId) {
    return null;
  }

  if (blocked) {
    return <span role="status">차단했습니다.</span>;
  }

  return (
    <>
      <button type="button" onClick={handleClick}>
        차단
      </button>
      {message ? <span role="status">{message}</span> : null}
    </>
  );
}
