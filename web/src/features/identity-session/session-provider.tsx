"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import { claimDevice, fetchSession, SESSION_RESOURCE_KEY } from "@/entities/user-session/api";
import type { CommunitySession } from "@/entities/user-session/schema";
import { readDeviceToken, rotateDeviceTokenAfterSuccessfulClaim } from "@/shared/api/device-token";
import { invalidateResource, useResource } from "@/shared/hooks/use-resource";

import { hasClaimSettled, markClaimSettled } from "./claim-flag";

/**
 * 세션 상태머신 — improvement_fe.md §14.5 (완전 명세, 재현 필수)
 *
 * 이 파일은 재작성 전체에서 가장 위험한 코드다. 여기의 규칙 하나를 놓치면 사용자의
 * 저장 기록이 사라지거나(§25.2) 로그인 자체가 불가능해진다. 규칙마다 "왜"를 남긴다.
 *
 * 불변식
 * - I-1: claimStatus가 error여도 loggedIn이면 소유자 스코프 조회는 계속 진행한다.
 *        claim은 익명 기록을 옮기는 부가 작업이지 로그인의 전제 조건이 아니다.
 * - I-2: 디바이스 토큰 회전은 claim **성공** 경로에서만.
 * - I-3: 조회 실패를 { loggedIn: false }로 축약하지 않는다.
 * - I-4: 세션 스코프 밖 경로에서는 세션 API를 호출하지 않는다.
 *        (이 프로바이더가 (session) 셸에만 마운트되는 구조로 이미 강제돼 있다.)
 */

export type ClaimStatus = "idle" | "claiming" | "settled" | "error";

export type SessionState = {
  /** null은 "모름"이다 — 비로그인(loggedIn: false)과 다르다(I-3). */
  session: CommunitySession | null;
  loading: boolean;
  /** 조회 실패. 비로그인과 반드시 구분한다. */
  error: boolean;
  claimStatus: ClaimStatus;
  retry: () => void;
};

const SessionContext = createContext<SessionState | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const state = useResource<CommunitySession>(SESSION_RESOURCE_KEY, fetchSession);
  const [claimStatus, setClaimStatus] = useState<ClaimStatus>("idle");

  const session = state.status === "success" ? state.data : null;
  const loggedIn = session?.loggedIn === true;

  useEffect(() => {
    if (!loggedIn) return;

    let cancelled = false;

    // claim 여부 판단은 sessionStorage·localStorage를 읽는다 — 렌더 중에 하면 서버와
    // 클라이언트의 결과가 달라 하이드레이션이 어긋난다. 그래서 상태 전이 전체를
    // 마이크로태스크 뒤로 미룬다.
    void Promise.resolve().then(() => {
      if (cancelled) return;

      // 이미 이 탭에서 끝냈거나, 익명으로 저장한 적이 없어 옮길 기록이 없는 경우다.
      if (hasClaimSettled() || readDeviceToken() === null) {
        setClaimStatus("settled");
        return;
      }

      setClaimStatus("claiming");
      return claimDeviceAndSettle();
    });

    function claimDeviceAndSettle() {
      return claimDevice().then(
        () => {
          if (cancelled) return;
          // 순서가 중요하다: 성공을 확인한 뒤에만 플래그를 남기고 토큰을 회전한다(I-2).
          markClaimSettled();
          rotateDeviceTokenAfterSuccessfulClaim();
          setClaimStatus("settled");
          // 계정 보관함이 방금 늘어났다 — 사용자별 캐시를 비워 다시 읽게 한다.
          invalidateResource("me:");
        },
        () => {
          if (cancelled) return;
          // 실패(409 포함)를 사용자에게 오류로 노출하지 않는다. 사용자가 할 수 있는 일이
          // 없고, 로그인 자체는 성공했기 때문이다. 상태만 error로 두어 소비자가
          // claiming에 멈추지 않게 한다.
          //
          // **플래그 기록도 토큰 회전도 하지 않는다** — 다음 탭에서 다시 시도할 수 있어야
          // 하고, 옮기지 못한 기록에 접근 경로를 잃으면 안 된다(I-2).
          setClaimStatus("error");
        },
      );
    }

    return () => {
      cancelled = true;
    };
  }, [loggedIn]);

  const retry = useCallback(() => {
    if (state.status === "error") state.retry();
  }, [state]);

  const value = useMemo<SessionState>(
    () => ({
      session,
      loading: state.status === "loading",
      error: state.status === "error",
      claimStatus: loggedIn ? claimStatus : state.status === "success" ? "settled" : "idle",
      retry,
    }),
    [session, state.status, loggedIn, claimStatus, retry],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

/**
 * 세션 셸 밖에서 부르면 스코프를 벗어난 것이다(I-4). 그 경우 조용히 기본값을 주는 대신
 * 던진다 — 조용한 기본값은 "공개 라우트에서 세션을 읽는" 실수를 숨긴다.
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
