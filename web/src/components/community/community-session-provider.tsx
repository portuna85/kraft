"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { getCommunitySession, type CommunitySession } from "@/lib/community-client";

type CommunitySessionState = {
  session: CommunitySession | null;
  loading: boolean;
};

const CommunitySessionContext = createContext<CommunitySessionState>({
  session: null,
  loading: true,
});

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
