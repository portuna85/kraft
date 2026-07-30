"use client";

import { useEffect, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
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
  const [savedNumbers, setSavedNumbers] = useState<MySavedNumber[] | null>(null);
  const [recommendationSets, setRecommendationSets] = useState<RecommendationSetSummary[] | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    if (!session?.loggedIn) return;
    let cancelled = false;
    Promise.all([getMySavedNumbers(), getMyRecommendationSets()])
      .then(([saved, sets]) => {
        if (!cancelled) {
          setSavedNumbers(saved);
          setRecommendationSets(sets);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [session?.loggedIn, retryKey]);

  if (loading || !session?.loggedIn) {
    return null;
  }

  if (loadError) {
    return (
      <section className="saved-account-library" aria-live="polite">
        <h2>계정에 연결된 기록</h2>
        <p className="status-text">계정 보관함을 불러오지 못했습니다.</p>
        <button type="button" onClick={() => { setLoadError(false); setRetryKey((value) => value + 1); }}>다시 시도</button>
      </section>
    );
  }

  const hasAccountData =
    (savedNumbers && savedNumbers.length > 0) || (recommendationSets && recommendationSets.length > 0);

  if (savedNumbers !== null && recommendationSets !== null && !hasAccountData) {
    return null;
  }

  return (
    <section className="saved-account-library">
      <h2>계정에 연결된 기록</h2>
      {savedNumbers && savedNumbers.length > 0 ? (
        <ul className="saved-list">
          {savedNumbers.map((item) => (
            <li key={item.id} className="saved-item">
              <LottoBalls numbers={item.numbers} />
            </li>
          ))}
        </ul>
      ) : null}
      {recommendationSets && recommendationSets.length > 0 ? (
        <ul className="saved-list">
          {recommendationSets.map((set) => (
            <li key={set.id} className="saved-item">
              {set.items.map((item) => (
                <LottoBalls key={item.position} numbers={item.numbers} />
              ))}
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
