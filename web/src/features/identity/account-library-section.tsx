"use client";

import { useEffect, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { formatDateTime } from "@/lib/format";
import { ErrorState } from "@/ui/primitives/error-state";
import { EmptyState } from "@/ui/primitives/empty-state";
import { asyncError, asyncLoading, asyncSuccess, type AsyncState } from "@/lib/async-state";

/** 계정 보관함의 두 목록은 한 번에 조회하고 함께 성공/실패하므로 한 상태로 묶는다. */
type AccountLibrary = {
  savedNumbers: MySavedNumber[];
  recommendationSets: RecommendationSetSummary[];
};
import { useCommunitySession } from "@/lib/community-session-provider";
import {
  getMyRecommendationSets,
  getMySavedNumbers,
  type MySavedNumber,
} from "@/lib/community-client";
import type { RecommendationSetSummary } from "@/features/recommendation/types";

/**
 * 로그인 계정으로 귀속된 저장 번호·추천 이력의 "통합 보관함" — 새 라우트 없이
 * /saved 페이지에 이 섹션을 더해 보여준다. 익명 기기 토큰 목록(SavedNumbersClient)과는
 * 별개로 계정(owner_user_id) 기준 데이터만 다룬다.
 */
export function AccountLibrarySection() {
  const { session, loading } = useCommunitySession();
  // FE-036: 두 목록을 각각 `T[] | null`로 두고 loadError를 따로 들면
  // "실패했는데 데이터도 있다" 같은 조합이 타입상 가능하고, 로딩 판정도
  // `savedNumbers === null && recommendationSets === null && !loadError`처럼
  // 세 값을 조합해 추론해야 했다. 두 조회는 함께 성공/실패하므로 한 상태로 묶는다.
  const [state, setState] = useState<AsyncState<AccountLibrary>>(asyncLoading);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    if (!session?.loggedIn) return;
    let cancelled = false;
    Promise.all([getMySavedNumbers(), getMyRecommendationSets()])
      .then(([saved, setsPage]) => {
        if (!cancelled) setState(asyncSuccess({ savedNumbers: saved, recommendationSets: setsPage.items }));
      })
      .catch(() => {
        if (!cancelled) setState(asyncError);
      });
    return () => {
      cancelled = true;
    };
  }, [session?.loggedIn, retryKey]);

  // 비로그인·미조회 상태에서는 이 섹션 자체가 의미가 없으므로 렌더하지 않는다.
  if (!session?.loggedIn) {
    return null;
  }

  // FE-040: 이전에는 로딩 중과 "계정 기록 없음"이 모두 null이라, 로그인 사용자에게
  // 섹션이 나타났다 사라지거나 아예 존재를 모르는 상태가 됐다.
  if (loading || state.status === "loading" || state.status === "idle") {
    return (
      <section className="saved-account-library" aria-busy="true">
        <h2>계정에 연결된 기록</h2>
        <span className="skeleton-line skeleton-body" />
        <span className="skeleton-line skeleton-body" />
      </section>
    );
  }

  if (state.status === "error") {
    return (
      <section className="saved-account-library">
        <h2>계정에 연결된 기록</h2>
        <ErrorState
          variant="inline"
          title="계정 보관함을 불러오지 못했습니다."
          retry={{
            label: "다시 시도",
            onClick: () => {
              setState(asyncLoading);
              setRetryKey((current) => current + 1);
            },
          }}
        />
      </section>
    );
  }

  const { savedNumbers, recommendationSets } = state.data;
  const hasAccountData = savedNumbers.length > 0 || recommendationSets.length > 0;

  // 빈 상태도 숨기지 않는다 — 로그인했는데 섹션이 사라지면 기능이 없는 것으로 오해한다.
  if (!hasAccountData) {
    return (
      <section className="saved-account-library">
        <h2>계정에 연결된 기록</h2>
        {/* FE-009: 다른 화면과 같은 빈 상태 표현을 쓴다. */}
        <EmptyState
          title="아직 계정에 연결된 기록이 없습니다."
          description="이 기기에서 저장한 번호는 로그인 시 계정으로 옮겨집니다."
        />
      </section>
    );
  }

  return (
    <section className="saved-account-library">
      <h2>계정에 연결된 기록</h2>
      <p className="muted">이 기기 목록과 별개로, 로그인한 계정에 연결된 기록입니다.</p>
      {savedNumbers && savedNumbers.length > 0 ? (
        <>
          <h3>저장 번호</h3>
          <ul className="saved-list">
            {savedNumbers.map((item) => (
              <li key={item.id} className="saved-item">
                <LottoBalls numbers={item.numbers} />
                {/* FE-040: 이전에는 번호만 나열해 언제 어디서 저장한 것인지 알 수 없었다. */}
                <p className="muted">
                  {item.label ? `${item.label} · ` : ""}
                  <time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time>
                </p>
              </li>
            ))}
          </ul>
        </>
      ) : null}
      {recommendationSets && recommendationSets.length > 0 ? (
        <>
          <h3>추천 이력</h3>
          <ul className="saved-list">
            {recommendationSets.map((set) => (
              <li key={set.id} className="saved-item">
                {set.items.map((item) => (
                  <LottoBalls key={item.position} numbers={item.numbers} />
                ))}
                <p className="muted">
                  <time dateTime={set.createdAt}>{formatDateTime(set.createdAt)}</time>
                </p>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </section>
  );
}
