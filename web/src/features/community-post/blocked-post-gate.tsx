"use client";

import { useEffect, useState, type ReactNode } from "react";

import { blockUser, getBlockedUsers } from "@/entities/community-post/interactions";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/states";

import styles from "./post-actions.module.css";

/**
 * 차단 게이팅 — improvement_fe.md §5.4, §25.6
 *
 * **서버는 항상 본문을 렌더하고, 가리는 일은 클라이언트가 한다.** 성능 때문이 아니라
 * 보안 모델의 귀결이다 — 공개 ISR HTML에는 사용자 상태를 넣지 않는다. 서버에서 거르려면
 * 차단 목록을 알아야 하고, 그러면 그 HTML은 그 사용자 전용이 되어 공유 캐시에 담을 수
 * 없다. "성능상 서버에서 거르자"로 바꾸면 이 모델이 깨진다.
 *
 * 차단 목록을 못 읽으면 **가리지 않는다.** 못 읽었다고 본문을 숨기면, 차단한 적 없는
 * 사용자가 네트워크 문제로 글을 못 보게 된다.
 */
export function BlockedPostGate({ ownerId, children }: { ownerId: number; children: ReactNode }) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const [blocked, setBlocked] = useState(false);
  const [revealed, setRevealed] = useState(false);

  useEffect(() => {
    if (!loggedIn) return;

    let cancelled = false;
    getBlockedUsers()
      .then((ids) => {
        if (!cancelled) setBlocked(ids.includes(ownerId));
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [ownerId, loggedIn]);

  if (!blocked || revealed) return <>{children}</>;

  return (
    <EmptyState
      reason="filtered"
      title="차단한 사용자의 글입니다"
      description="차단을 해제하거나, 이번만 내용을 확인할 수 있습니다."
      action={
        <Button variant="secondary" onClick={() => setRevealed(true)}>
          이번만 보기
        </Button>
      }
    />
  );
}

/**
 * 차단·해제 버튼. 자기 자신은 차단할 수 없으므로 본인 글에서는 아예 렌더하지 않는다.
 */
export function BlockButton({ ownerId }: { ownerId: number }) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const [blocked, setBlocked] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!loggedIn) return;

    let cancelled = false;
    getBlockedUsers()
      .then((ids) => {
        if (!cancelled) setBlocked(ids.includes(ownerId));
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [ownerId, loggedIn]);

  if (!loggedIn || session.session?.userId === ownerId) return null;

  async function toggle() {
    const next = !blocked;
    setPending(true);
    setError(null);
    try {
      await blockUser(ownerId, next);
      setBlocked(next);
    } catch {
      setError("차단 상태를 변경하지 못했습니다.");
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <Button variant="quiet" onClick={toggle} disabled={pending} aria-pressed={blocked}>
        {blocked ? "차단 해제" : "이 사용자 차단"}
      </Button>
      {error !== null && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </>
  );
}
