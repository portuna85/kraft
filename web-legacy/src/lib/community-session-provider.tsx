"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { claimDevice, getCommunitySession, type CommunitySession } from "@/lib/community-client";
import { rotateDeviceToken } from "@/lib/device-token";

/**
 * C-2: 디바이스 귀속(claim) 진행 상태. 소비자(저장번호/추천이력 등)가 이 값으로
 * "귀속이 끝나서 계정 데이터를 다시 읽어도 되는 시점"을 알 수 있게 한다.
 * - idle: 아직 귀속 여부를 판단하기 전(세션 조회 중이거나 비스코프 경로).
 * - claiming: claim() 요청이 진행 중.
 * - settled: 귀속이 성공했거나, 애초에 필요 없었음(비로그인·이번 세션에 이미 시도함).
 * - error: claim() 자체가 실패(네트워크 오류 등). 409(이미 다른 계정에 귀속)는 여기
 *   포함하지 않는다 — 사용자에게 노출할 오류가 아니라 그냥 이번 시도가 무의미했을 뿐이다.
 */
export type ClaimStatus = "idle" | "claiming" | "settled" | "error";

type CommunitySessionState = {
  session: CommunitySession | null;
  loading: boolean;
  /**
   * FE-005: 세션 조회가 실패했다는 사실. 이전에는 실패를
   * `{ loggedIn: false, activeProviders: [] }`로 축약해 "비로그인"과 구분되지 않았고,
   * activeProviders가 비어 로그인 링크마저 사라져 복구 경로가 없었다. 실패는 실패로
   * 남기고(session은 null = 알 수 없음) 소비자가 재시도를 제공할 수 있게 한다.
   */
  error: boolean;
  claimStatus: ClaimStatus;
  retry: () => void;
};

const NOOP_RETRY = () => {};

const CommunitySessionContext = createContext<CommunitySessionState>({
  session: null,
  loading: true,
  error: false,
  claimStatus: "idle",
  retry: NOOP_RETRY,
});

// 로그인 세션이 확인될 때마다 매번 귀속을 재시도하지 않도록 탭 세션 동안만 유지되는 플래그.
// 귀속 자체는 서버에서 멱등하므로 정확성 문제는 아니지만, 페이지 이동마다 불필요한 요청을
// 반복하지 않기 위함이다.
const CLAIM_ATTEMPTED_KEY = "kraft-device-claim-attempted";

// KF-05: 세션이 실제로 필요한 라우트만 스코핑한다 — 홈·통계·회차 등 완전 공개 페이지는
// 이 접두어들에 해당하지 않으면 세션 API를 아예 호출하지 않는다(익명 방문 세션 트래픽 제거).
// C-2: /recommend를 추가한다 — 저장·이력 조회가 로그인 여부에 따라 계정/기기 스코프
// 엔드포인트로 갈라져야 하므로(use-recommendation-studio.ts, recommendation-history-client.tsx)
// 이 경로도 로그인 상태를 알아야 한다.
const SESSION_SCOPED_PREFIXES = ["/community", "/saved", "/recommend"];

export function isSessionScopedPath(pathname: string): boolean {
  return SESSION_SCOPED_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

// R-43: AccountMenu·PostOwnerActions·CommentSection·PostForm이 각자
// getCommunitySession()을 호출하던 것(페이지당 최대 3~4회, no-store라 dedupe도 없음)을
// 페이지 트리 최상단에서 1회만 조회하도록 통합한다. KF-05: 이후 이 통합 조회 자체가
// 익명 공개 페이지에서도 매번 나가던 문제를 스코핑으로 없앤다 — 스코프 밖에서는
// session을 null로 유지해 "아직 안 불렀음"과 "불렀는데 비로그인"을 구분한다
// (AccountMenu가 이 둘을 다르게 렌더링한다).
const UNSCOPED_STATE: CommunitySessionState = {
  session: null,
  loading: false,
  error: false,
  claimStatus: "idle",
  retry: NOOP_RETRY,
};

type FetchState = Pick<CommunitySessionState, "session" | "loading" | "error" | "claimStatus">;

export function CommunitySessionProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const scoped = isSessionScopedPath(pathname);
  const [state, setState] = useState<FetchState>(
    scoped
      ? { session: null, loading: true, error: false, claimStatus: "idle" }
      : { session: null, loading: false, error: false, claimStatus: "idle" }
  );
  const [retryKey, setRetryKey] = useState(0);

  const retry = useCallback(() => {
    setState({ session: null, loading: true, error: false, claimStatus: "idle" });
    setRetryKey((value) => value + 1);
  }, []);

  useEffect(() => {
    // 스코프 밖에서는 아무 것도 조회하지 않는다 — 렌더 값은 아래 value가 UNSCOPED_STATE로
    // 고정하므로, 여기서는 effect cleanup 대상(진행 중 fetch)이 없다는 것만 보장하면 된다.
    if (!scoped) {
      return;
    }
    let cancelled = false;
    getCommunitySession()
      .then((session) => {
        if (cancelled) return;
        // C-2: claim이 필요 없는 경우(비로그인, 이번 세션에 이미 시도함)는 즉시 settled로
        // 둔다 — 소비자가 계정 데이터 조회를 기다릴 이유가 없다.
        const needsClaim = session.loggedIn && !window.sessionStorage.getItem(CLAIM_ATTEMPTED_KEY);
        setState({ session, loading: false, error: false, claimStatus: needsClaim ? "claiming" : "settled" });
        if (needsClaim) {
          // F-P0-10: 이전에는 성공/실패와 무관하게 토큰을 회전시켰다 — claim이 실패하면
          // (네트워크 오류 등, 409 "이미 귀속됨"과는 다른 경우) 회전된 새 토큰은 서버에
          // 아무 데이터도 연결돼 있지 않아 이 브라우저의 익명 활동 이력에 계속 접근할 수
          // 없게 된다. 성공했을 때만 회전하고, 실패 시엔 플래그도 세우지 않아 다음 세션
          // 조회 때 같은(유효한) 토큰으로 재시도할 수 있게 한다.
          claimDevice()
            .then(() => {
              if (cancelled) return;
              window.sessionStorage.setItem(CLAIM_ATTEMPTED_KEY, "1");
              rotateDeviceToken();
              setState((prev) => ({ ...prev, claimStatus: "settled" }));
            })
            .catch(() => {
              // 409(다른 계정이 이미 귀속) 등은 조용히 무시 — 사용자에게 에러로 노출하지
              // 않는다. 회전은 하지 않으므로 다음 세션 조회 시 멱등하게 재시도된다.
              // claimStatus만 error로 남겨 소비자가 "claiming으로 멈춰있지 않게" 한다 —
              // 소유자 스코프 데이터 조회 자체는 loggedIn 기준으로 계속 진행돼야 한다.
              if (!cancelled) setState((prev) => ({ ...prev, claimStatus: "error" }));
            });
        }
      })
      .catch(() => {
        // 실패를 비로그인으로 위장하지 않는다 — session은 null(알 수 없음)로 두고
        // error로 표시해 소비자가 재시도를 제시할 수 있게 한다.
        if (!cancelled) {
          setState({ session: null, loading: false, error: true, claimStatus: "idle" });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [scoped, retryKey]);

  const value = useMemo<CommunitySessionState>(
    () => (scoped ? { ...state, retry } : UNSCOPED_STATE),
    [scoped, state, retry]
  );

  return (
    <CommunitySessionContext.Provider value={value}>{children}</CommunitySessionContext.Provider>
  );
}

export function useCommunitySession(): CommunitySessionState {
  return useContext(CommunitySessionContext);
}
