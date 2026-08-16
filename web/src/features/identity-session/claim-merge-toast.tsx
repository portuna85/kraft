"use client";

import { useEffect, useRef } from "react";

import { useSession } from "@/entities/user-session/session-context";
import { useToast } from "@/shared/ui/toast";

/**
 * UX-04: 로그인 시 이 브라우저에 익명으로 저장했던 번호가 계정으로 옮겨져도
 * 알림이 전혀 없었다 — claimStatus 상태머신(session-provider.tsx)은 이미 있었지만
 * 아무도 구독하지 않았다.
 *
 * "claiming → settled" 전이일 때만 실제 병합이 일어난 것이다. 옮길 기록이 애초에
 * 없던 로그인(§session-provider.tsx I-1 인접 주석의 즉시 settle 경로)은 claiming을
 * 거치지 않고 idle에서 바로 settled로 가므로 이 전이에 걸리지 않는다 — 매 로그인마다
 * 잘못된 토스트가 뜨는 것을 막는 핵심 조건이다.
 */
export function ClaimMergeToast() {
  const { claimStatus } = useSession();
  const { show } = useToast();
  const previous = useRef(claimStatus);

  useEffect(() => {
    if (previous.current === "claiming" && claimStatus === "settled") {
      show("이 브라우저에 저장했던 번호를 계정으로 옮겼습니다.");
    }
    previous.current = claimStatus;
  }, [claimStatus, show]);

  return null;
}
