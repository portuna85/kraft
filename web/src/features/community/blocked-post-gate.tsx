"use client";

import type { ReactNode } from "react";
import { useBlockedUserIds } from "@/features/community/blocked-users-context";
import { BlockButton } from "@/features/community/block-button";

/**
 * C-1: 서버가 내려준 게시글은 공유 ISR 캐시라 사용자별 차단 여부를 반영하지 않는다
 * (community-api.ts:9-11 계약 유지). 차단 여부는 여기, 클라이언트 렌더링 시점에만
 * 확인해 본문 대신 대체 상태를 보여준다 — 완전히 숨기면(404 등) URL 직접 접근 시
 * 아무 설명 없는 빈 화면만 보여 혼란스럽다. BlockButton은 그대로 남겨 해제 경로를 제공한다.
 *
 * `features/**`는 `components/**`를 참조할 수 없어(KF-08 레이어 규칙) PageHeader 등을
 * 이 컴포넌트 안에서 직접 쓸 수 없다 — 그래서 게이트는 얇게 유지하고, 실제 콘텐츠
 * 조립(PageHeader + 이 게이트)은 둘 다 참조 가능한 app/ 페이지 쪽에서 한다.
 */
export function BlockedPostGate({ ownerId, children }: { ownerId: number; children: ReactNode }) {
  const { isBlocked } = useBlockedUserIds();

  if (isBlocked(ownerId)) {
    return (
      <>
        <p className="muted">이 게시글은 차단한 사용자가 작성했습니다. 차단을 해제하면 다시 볼 수 있습니다.</p>
        <BlockButton userId={ownerId} />
      </>
    );
  }

  return <>{children}</>;
}
