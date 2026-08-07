"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { getMyBlockedUserIds } from "@/lib/community-client";
import { useCommunitySession } from "@/lib/community-session-provider";

type BlockedUsersState = {
  blockedUserIds: Set<number>;
  isBlocked: (userId: number) => boolean;
  markBlocked: (userId: number) => void;
  markUnblocked: (userId: number) => void;
};

const EMPTY_SET: Set<number> = new Set();

const BlockedUsersContext = createContext<BlockedUsersState>({
  blockedUserIds: EMPTY_SET,
  isBlocked: () => false,
  markBlocked: () => {},
  markUnblocked: () => {},
});

/**
 * C-1: 차단 목록을 커뮤니티 페이지 트리에서 한 번만 조회해 피드·상세·댓글 렌더링과
 * BlockButton 초기 상태가 모두 이 값을 구독하게 한다. 예전에는 ReactionBar가 게시글마다
 * getMyInteractions로 blockedUserIds를 받고도 버렸다 — 렌더링 어디에도 반영되지 않았다.
 * 서버 응답을 걸러내는 것뿐이므로(§ web/src/lib/community-api.ts:9-11의 공개 ISR 캐시
 * 계약은 그대로 둔다), 차단 강제 자체는 백엔드 쓰기 경로(CommunityCommentService/
 * CommunityReactionService)가 별도로 한다 — 이 컨텍스트가 실패해도 안전은 깨지지 않는다.
 */
export function BlockedUsersProvider({ children }: { children: ReactNode }) {
  const { session } = useCommunitySession();
  const [fetchedIds, setFetchedIds] = useState<Set<number>>(EMPTY_SET);
  // 비로그인 상태에서는 effect에서 동기적으로 상태를 되돌리지 않는다(불필요한 렌더 캐스케이드
  // 방지) — 대신 노출값 자체를 렌더 중에 파생시킨다. fetchedIds는 로그인 상태에서 조회한
  // 마지막 결과를 그대로 들고 있어도 무해하다: 비로그인일 때는 아래에서 항상 무시된다.
  const blockedUserIds = session?.loggedIn ? fetchedIds : EMPTY_SET;

  useEffect(() => {
    if (!session?.loggedIn) return;
    let cancelled = false;
    getMyBlockedUserIds()
      .then((ids) => {
        if (!cancelled) setFetchedIds(new Set(ids));
      })
      .catch(() => {
        // 조회 실패는 조용히 무시한다 — 표시 필터링은 여러 방어선 중 하나일 뿐이고
        // 쓰기 경로는 서버가 별도로 강제하므로, 실패했다고 화면을 막을 이유가 없다.
      });
    return () => {
      cancelled = true;
    };
  }, [session?.loggedIn]);

  const isBlocked = useCallback((userId: number) => blockedUserIds.has(userId), [blockedUserIds]);

  const markBlocked = useCallback((userId: number) => {
    setFetchedIds((prev) => new Set(prev).add(userId));
  }, []);

  const markUnblocked = useCallback((userId: number) => {
    setFetchedIds((prev) => {
      const next = new Set(prev);
      next.delete(userId);
      return next;
    });
  }, []);

  const value = useMemo<BlockedUsersState>(
    () => ({ blockedUserIds, isBlocked, markBlocked, markUnblocked }),
    [blockedUserIds, isBlocked, markBlocked, markUnblocked]
  );

  return <BlockedUsersContext.Provider value={value}>{children}</BlockedUsersContext.Provider>;
}

export function useBlockedUserIds(): BlockedUsersState {
  return useContext(BlockedUsersContext);
}
