"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import { claimDevice, fetchSession, SESSION_RESOURCE_KEY } from "@/entities/user-session/api";
import type { CommunitySession } from "@/entities/user-session/schema";
import {
  SessionContext,
  type ClaimStatus,
  type SessionState,
} from "@/entities/user-session/session-context";
import { readDeviceToken, rotateDeviceTokenAfterSuccessfulClaim } from "@/shared/api/device-token";
import { invalidateResource, useResource } from "@/shared/hooks/use-resource";

import { hasClaimSettled, markClaimSettled } from "./claim-flag";

/**
 * 세션 상태머신 — 아래가 이 상태머신의 완전한 명세다(다른 문서를 참조하지 않는다).
 *
 * 이 파일은 재작성 전체에서 가장 위험한 코드다. 여기의 규칙 하나를 놓치면 사용자의
 * 저장 기록이 사라지거나 로그인 자체가 불가능해진다. 규칙마다 "왜"를 남긴다.
 *
 * 불변식
 * - I-1: claimStatus가 error여도 loggedIn이면 소유자 스코프 조회는 계속 진행한다.
 *        claim은 익명 기록을 옮기는 부가 작업이지 로그인의 전제 조건이 아니다.
 * - I-2: 디바이스 토큰 회전은 claim **성공** 경로에서만.
 * - I-3: 조회 실패를 { loggedIn: false }로 축약하지 않는다.
 * - I-4: 익명 방문자에게는 세션 API를 호출하지 않는다.
 *        (이 프로바이더는 서버에서 `kraft_logged_in` 쿠키로 로그인이 이미 확인된
 *        사용자에 한해서만 마운트된다 — (session) 셸 전체, 또는 (public) 셸의
 *        `PublicAccountMenu`. 익명 방문자 경로에는 이 컴포넌트 자체가 없다.)
 */

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
