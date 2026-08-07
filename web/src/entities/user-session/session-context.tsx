"use client";

import { createContext, useContext } from "react";

import type { CommunitySession } from "./schema";

/**
 * 세션 접근 지점 — improvement_fe.md §7.3
 *
 * Context 자체가 features가 아니라 entity에 있는 이유는 계층 규칙이 알려줬다.
 * recommend-studio가 identity-session을 직접 참조하려다 ESLint에 막혔고, 그것이
 * "두 feature가 같은 것을 필요로 하면 entities로 올라가야 할 개념"이라는 신호였다
 * (§7.3 주석). 세션 값은 도메인 모델이고, claim 상태머신은 시나리오다 — 그래서
 * 값과 접근 훅은 여기, 오케스트레이션은 features/identity-session에 남는다.
 */

export type ClaimStatus = "idle" | "claiming" | "settled" | "error";

export type SessionState = {
  /** null은 "모름"이다 — 비로그인(loggedIn: false)과 다르다(불변식 I-3). */
  session: CommunitySession | null;
  loading: boolean;
  /** 조회 실패. 비로그인과 반드시 구분한다. */
  error: boolean;
  claimStatus: ClaimStatus;
  retry: () => void;
};

export const SessionContext = createContext<SessionState | null>(null);

/**
 * 세션 셸 밖에서 부르면 조용한 기본값 대신 던진다(I-4). 기본값을 주면
 * "공개 라우트에서 세션을 읽는" 실수가 숨는다.
 */
export function useSession(): SessionState {
  const context = useContext(SessionContext);
  if (context === null) {
    throw new Error(
      "useSession은 (session) 셸 안에서만 쓸 수 있습니다 — 공개 라우트는 세션을 조회하지 않습니다.",
    );
  }
  return context;
}

/**
 * 소유자 스코프 조회를 진행해도 되는지. **claimStatus는 보지 않는다**(I-1) —
 * claim 실패는 익명 기록 이전에 실패한 것일 뿐, 계정 데이터 조회와는 무관하다.
 */
export function canQueryOwnerScope(state: SessionState): boolean {
  return state.session?.loggedIn === true;
}
