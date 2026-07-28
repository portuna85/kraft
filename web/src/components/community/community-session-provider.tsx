"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { claimDevice, getCommunitySession, type CommunitySession } from "@/lib/community-client";
import { rotateDeviceToken } from "@/lib/device-token";

type CommunitySessionState = {
  session: CommunitySession | null;
  loading: boolean;
};

const CommunitySessionContext = createContext<CommunitySessionState>({
  session: null,
  loading: true,
});

// 로그인 세션이 확인될 때마다 매번 귀속을 재시도하지 않도록 탭 세션 동안만 유지되는 플래그.
// 귀속 자체는 서버에서 멱등하므로 정확성 문제는 아니지만, 페이지 이동마다 불필요한 요청을
// 반복하지 않기 위함이다.
const CLAIM_ATTEMPTED_KEY = "kraft-device-claim-attempted";

// R-43: AccountMenu·PostOwnerActions·CommentSection·PostForm이 각자
// getCommunitySession()을 호출하던 것(페이지당 최대 3~4회, no-store라 dedupe도 없음)을
// 페이지 트리 최상단에서 1회만 조회하도록 통합한다.
export function CommunitySessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<CommunitySessionState>({ session: null, loading: true });

  useEffect(() => {
    let cancelled = false;
    getCommunitySession()
      .then((session) => {
        if (!cancelled) setState({ session, loading: false });
        if (session.loggedIn && !window.sessionStorage.getItem(CLAIM_ATTEMPTED_KEY)) {
          window.sessionStorage.setItem(CLAIM_ATTEMPTED_KEY, "1");
          // 귀속 성공·실패(다른 계정이 이미 귀속한 기기 토큰 등)와 무관하게 토큰을 회전한다 —
          // 성공 시엔 이전 토큰이 더 이상 아무 것도 가리키지 않으므로, 실패 시엔 이 브라우저가
          // 계속 같은 토큰으로 재시도해 매번 충돌하는 것을 막기 위해서다(문서 10.2 8단계).
          claimDevice()
            .catch(() => {
              // 409(다른 계정이 이미 귀속) 등은 조용히 무시 — 사용자에게 에러로 노출하지 않는다.
            })
            .finally(() => {
              rotateDeviceToken();
            });
        }
      })
      .catch(() => {
        if (!cancelled) {
          setState({
            session: { loggedIn: false, userId: null, nickname: null, activeProviders: [] },
            loading: false,
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <CommunitySessionContext.Provider value={state}>{children}</CommunitySessionContext.Provider>
  );
}

export function useCommunitySession(): CommunitySessionState {
  return useContext(CommunitySessionContext);
}
