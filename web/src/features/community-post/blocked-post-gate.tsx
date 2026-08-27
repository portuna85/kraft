"use client";

import { useState, type ReactNode } from "react";

import { blockUser } from "@/entities/community-post/interactions";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { invalidateResource } from "@/shared/hooks/use-resource";
import { Button } from "@/shared/ui/button";
import { EmptyState, InlineAlert } from "@/shared/ui/states";

import { usePostInteractions } from "./use-post-interactions";

/**
 * 차단 게이팅
 *
 * **서버는 항상 본문을 렌더하고, 가리는 일은 클라이언트가 한다.** 성능 때문이 아니라
 * 보안 모델의 귀결이다 — 공개 ISR HTML에는 사용자 상태를 넣지 않는다. 서버에서 거르려면
 * 차단 목록을 알아야 하고, 그러면 그 HTML은 그 사용자 전용이 되어 공유 캐시에 담을 수
 * 없다. "성능상 서버에서 거르자"로 바꾸면 이 모델이 깨진다.
 *
 * 차단 목록을 못 읽으면 **가리지 않는다.** 못 읽었다고 본문을 숨기면, 차단한 적 없는
 * 사용자가 네트워크 문제로 글을 못 보게 된다.
 */
export function BlockedPostGate({
  postId,
  ownerId,
  children,
}: {
  postId: number;
  ownerId: number;
  children: ReactNode;
}) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const interactions = usePostInteractions(postId, loggedIn);
  const blocked = interactions?.blockedUserIds.includes(ownerId) ?? false;
  const [revealed, setRevealed] = useState(false);

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
 * I-33: 신고 게이팅. 자기 글은 신고 대상이 될 수 없는데 `ReportDialog`가 소유자
 * 구분 없이 항상 렌더돼 본인 글에도 "이 글 신고"가 보였다 — BlockButton과 같은
 * 클라이언트 측 노출 판단(§주석) 방식으로 작성자 본인에게는 감춘다.
 */
export function ReportGate({ ownerId, children }: { ownerId: number; children: ReactNode }) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  if (loggedIn && session.session?.userId === ownerId) return null;
  return <>{children}</>;
}

/**
 * 차단·해제 버튼. 자기 자신은 차단할 수 없으므로 본인 글에서는 아예 렌더하지 않는다.
 *
 * 차단 여부는 `BlockedPostGate`와 같은 `me:interactions:${postId}` 리소스를 공유한다.
 * 토글 성공 후 그 키를 무효화하면 이 버튼과 게이트가 같은 갱신 한 번으로 함께
 * 새로고침된다 — 낙관적 오버레이(`optimisticBlocked`)로 재조회가 끝나기 전에도
 * 클릭에 즉시 반응한다.
 */
export function BlockButton({ postId, ownerId }: { postId: number; ownerId: number }) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const interactions = usePostInteractions(postId, loggedIn);
  const [optimisticBlocked, setOptimisticBlocked] = useState<boolean | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const serverBlocked = interactions?.blockedUserIds.includes(ownerId) ?? false;
  const blocked = optimisticBlocked ?? serverBlocked;

  // 렌더 중 상태 조정(React 공식 패턴, effect 아님 — set-state-in-effect 린트 규칙이
  // effect 안에서의 setState를 금지한다). 무효화로 촉발된 재조회가 낙관적 값과 같은
  // 결과를 확인해 주면 그 렌더에서 바로 오버레이를 내린다. 재조회가 아직 옛 값을
  // 돌려주는 동안에는 지우지 않는다 — 그러면 "눌렀는데 잠깐 원상복구됐다 다시
  // 바뀐다"는 깜빡임이 생긴다.
  const [syncedServerBlocked, setSyncedServerBlocked] = useState(serverBlocked);
  if (serverBlocked !== syncedServerBlocked) {
    setSyncedServerBlocked(serverBlocked);
    if (optimisticBlocked === serverBlocked) setOptimisticBlocked(null);
  }

  if (!loggedIn || session.session?.userId === ownerId) return null;

  async function toggle() {
    const next = !blocked;
    setOptimisticBlocked(next);
    setPending(true);
    setError(null);
    try {
      await blockUser(ownerId, next);
      invalidateResource(`me:interactions:${postId}`);
    } catch {
      setOptimisticBlocked(!next);
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
      {error !== null && <InlineAlert tone="danger">{error}</InlineAlert>}
    </>
  );
}
