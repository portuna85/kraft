"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { formatDateTime } from "@/lib/format";
import { ErrorState } from "@/ui/primitives/error-state";
import { EmptyState } from "@/ui/primitives/empty-state";
import { ConfirmDialog } from "@/ui/primitives/confirm-dialog";
import { asyncError, asyncLoading, asyncSuccess, type AsyncState } from "@/lib/async-state";
import { useCommunitySession } from "@/lib/community-session-provider";
import {
  deleteMyRecommendationSet,
  deleteMySavedNumber,
  getMyRecommendationSets,
  getMySavedNumberMatches,
  getMySavedNumbers,
  type MySavedNumber,
  type MySavedNumberMatchResult,
} from "@/lib/community-client";
import type { RecommendationSetSummary } from "@/features/recommendation/types";

const PAGE_SIZE = 20;
const RECENT_ROUND_OPTIONS = 20;

type RecommendationHistory = {
  items: RecommendationSetSummary[];
  page: number;
  totalPages: number;
  totalElements: number;
};

/** 계정 보관함의 두 목록은 한 번에 조회하고 함께 성공/실패하므로 한 상태로 묶는다. */
type AccountLibrary = {
  savedNumbers: MySavedNumber[];
  recommendationSets: RecommendationHistory;
};

type MatchState = "idle" | "loading" | "success" | "error";
type PendingDelete = { kind: "saved" | "recommendation"; id: number } | null;

function isWin(prizeTier: string): boolean {
  return prizeTier !== "낙첨";
}

/**
 * 로그인 계정으로 귀속된 저장 번호·추천 이력의 "통합 보관함" — 새 라우트 없이
 * /saved 페이지에 이 섹션을 더해 보여준다. 익명 기기 토큰 목록(SavedNumbersClient)과는
 * 별개로 계정(owner_user_id) 기준 데이터만 다룬다.
 *
 * C-2: 예전에는 목록 표시만 했다 — 백엔드는 삭제·회차 대조·페이지네이션을 이미
 * 지원하는데 프론트가 GET 두 개만 썼다. 이제 SavedNumbersClient/RecommendationHistoryClient와
 * 동등한 관리 기능을 계정 스코프로 제공한다(로그인하면 기능이 없어지는 문제를 없앤다).
 */
export function AccountLibrarySection({ latestRound }: { latestRound: number }) {
  const { session, loading, claimStatus } = useCommunitySession();
  const isClaimSettling = claimStatus === "claiming";
  // FE-036: 두 목록을 각각 `T[] | null`로 두고 loadError를 따로 들면
  // "실패했는데 데이터도 있다" 같은 조합이 타입상 가능하고, 로딩 판정도
  // `savedNumbers === null && recommendationSets === null && !loadError`처럼
  // 세 값을 조합해 추론해야 했다. 두 조회는 함께 성공/실패하므로 한 상태로 묶는다.
  const [state, setState] = useState<AsyncState<AccountLibrary>>(asyncLoading);
  const [retryKey, setRetryKey] = useState(0);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [message, setMessage] = useState("");

  const [selectedRound, setSelectedRound] = useState<string>("latest");
  const [customRoundInput, setCustomRoundInput] = useState("");
  const [matchMap, setMatchMap] = useState<Map<number, MySavedNumberMatchResult>>(new Map());
  const [matchState, setMatchState] = useState<MatchState>("idle");
  const matchFetchSeqRef = useRef(0);

  const [pendingDelete, setPendingDelete] = useState<PendingDelete>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    // C-2: claim이 진행 중이면 귀속이 옮기는 도중의 데이터를 읽지 않는다 — settled로
    // 전이하면(의존성의 claimStatus) 다시 실행돼 최종 목록을 가져온다.
    if (!session?.loggedIn || isClaimSettling) return;
    let cancelled = false;
    Promise.all([getMySavedNumbers(), getMyRecommendationSets(0, PAGE_SIZE)])
      .then(([saved, setsPage]) => {
        if (!cancelled) {
          setState(
            asyncSuccess({
              savedNumbers: saved,
              recommendationSets: {
                items: setsPage.items,
                page: setsPage.page,
                totalPages: setsPage.totalPages,
                totalElements: setsPage.totalElements,
              },
            })
          );
        }
      })
      .catch(() => {
        if (!cancelled) setState(asyncError);
      });
    return () => {
      cancelled = true;
    };
  }, [session?.loggedIn, isClaimSettling, claimStatus, retryKey]);

  const fetchMatches = useCallback(() => {
    if (state.status !== "success" || state.data.savedNumbers.length === 0) return;
    const seq = ++matchFetchSeqRef.current;
    getMySavedNumberMatches(selectedRound)
      .then((results) => {
        if (seq !== matchFetchSeqRef.current) return;
        const map = new Map<number, MySavedNumberMatchResult>();
        for (const result of results) {
          map.set(result.savedNumber.id, result);
        }
        setMatchMap(map);
        setMatchState("success");
      })
      .catch(() => {
        if (seq !== matchFetchSeqRef.current) return;
        setMatchState("error");
      });
  }, [state, selectedRound]);

  useEffect(() => {
    fetchMatches();
  }, [fetchMatches]);

  function changeSelectedRound(round: string) {
    setMatchMap(new Map());
    setMatchState("loading");
    setSelectedRound(round);
  }

  function applyCustomRound(event: React.FormEvent) {
    event.preventDefault();
    const round = Number.parseInt(customRoundInput.trim(), 10);
    if (Number.isNaN(round) || round < 1 || round > latestRound) {
      return;
    }
    changeSelectedRound(String(round));
    setCustomRoundInput("");
  }

  function loadMore() {
    if (state.status !== "success") return;
    const current = state.data.recommendationSets;
    setIsLoadingMore(true);
    setMessage("");
    getMyRecommendationSets(current.page + 1, PAGE_SIZE)
      .then((result) => {
        setState((prev) => {
          if (prev.status !== "success") return prev;
          const seen = new Set(prev.data.recommendationSets.items.map((item) => item.id));
          return asyncSuccess({
            ...prev.data,
            recommendationSets: {
              items: [...prev.data.recommendationSets.items, ...result.items.filter((item) => !seen.has(item.id))],
              page: result.page,
              totalPages: result.totalPages,
              totalElements: result.totalElements,
            },
          });
        });
      })
      .catch(() => setMessage("다음 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."))
      .finally(() => setIsLoadingMore(false));
  }

  async function confirmDelete() {
    if (pendingDelete === null) return;
    const { kind, id } = pendingDelete;
    setDeleting(true);
    setDeleteError(null);
    try {
      if (kind === "saved") {
        await deleteMySavedNumber(id);
        setState((prev) =>
          prev.status === "success"
            ? asyncSuccess({ ...prev.data, savedNumbers: prev.data.savedNumbers.filter((x) => x.id !== id) })
            : prev
        );
      } else {
        await deleteMyRecommendationSet(id);
        setState((prev) =>
          prev.status === "success"
            ? asyncSuccess({
                ...prev.data,
                recommendationSets: {
                  ...prev.data.recommendationSets,
                  items: prev.data.recommendationSets.items.filter((x) => x.id !== id),
                  totalElements: Math.max(0, prev.data.recommendationSets.totalElements - 1),
                },
              })
            : prev
        );
      }
      setPendingDelete(null);
    } catch {
      setDeleteError(
        kind === "saved"
          ? "저장 번호를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요."
          : "추천 이력을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요."
      );
    } finally {
      setDeleting(false);
    }
  }

  // 비로그인·미조회 상태에서는 이 섹션 자체가 의미가 없으므로 렌더하지 않는다.
  if (!session?.loggedIn) {
    return null;
  }

  // FE-040: 이전에는 로딩 중과 "계정 기록 없음"이 모두 null이라, 로그인 사용자에게
  // 섹션이 나타났다 사라지거나 아예 존재를 모르는 상태가 됐다.
  if (loading || isClaimSettling || state.status === "loading" || state.status === "idle") {
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
  const hasAccountData = savedNumbers.length > 0 || recommendationSets.items.length > 0;

  const recentRoundOptions: number[] = [];
  for (let round = latestRound; round >= 1 && recentRoundOptions.length < RECENT_ROUND_OPTIONS; round--) {
    recentRoundOptions.push(round);
  }

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
      {message ? <p className="status-text" role="status" aria-live="polite">{message}</p> : null}

      {savedNumbers.length > 0 ? (
        <>
          <h3>저장 번호</h3>
          {latestRound > 0 ? (
            <div className="saved-round-controls">
              <label className="saved-round-selector">
                대조할 회차
                <select value={selectedRound} onChange={(event) => changeSelectedRound(event.target.value)}>
                  <option value="latest">최신 회차</option>
                  {recentRoundOptions.map((round) => (
                    <option key={round} value={round}>
                      {round}회
                    </option>
                  ))}
                </select>
              </label>
              <form onSubmit={applyCustomRound} className="saved-round-custom-form">
                <input
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={latestRound}
                  value={customRoundInput}
                  onChange={(event) => setCustomRoundInput(event.target.value)}
                  placeholder="회차 직접 입력"
                  aria-label="회차 직접 입력"
                />
                <button type="submit" className="button secondary">
                  적용
                </button>
              </form>
              {matchState === "loading" ? (
                <p className="saved-match-loading" aria-live="polite">
                  대조 결과를 불러오는 중입니다.
                </p>
              ) : matchState === "error" ? (
                <ErrorState
                  variant="inline"
                  title="대조 결과를 불러오지 못했습니다."
                  retry={{ label: "다시 시도", onClick: fetchMatches }}
                />
              ) : null}
            </div>
          ) : null}
          <ul className="saved-list">
            {savedNumbers.map((item) => {
              const match = matchMap.get(item.id);
              return (
                <li key={item.id} className="saved-item">
                  <div className="saved-item-row">
                    <LottoBalls numbers={item.numbers} />
                    <button
                      type="button"
                      className="saved-delete-btn"
                      onClick={() => setPendingDelete({ kind: "saved", id: item.id })}
                      aria-label={`${item.numbers.join(", ")} 조합 삭제`}
                    >
                      삭제
                    </button>
                  </div>
                  {/* FE-040: 이전에는 번호만 나열해 언제 어디서 저장한 것인지 알 수 없었다. */}
                  <p className="muted">
                    {item.label ? `${item.label} · ` : ""}
                    <time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time>
                  </p>
                  {match ? (
                    <div className="saved-match-info">
                      <span className="saved-draw-ref">{match.round}회 ({match.drawDate})</span>
                      <span className={`saved-prize-badge${isWin(match.prizeTier) ? " prize-win" : ""}`}>
                        {match.prizeTier}
                      </span>
                      <span>{match.matchedCount}개 일치</span>
                      {match.bonusMatch ? <span>보너스 일치</span> : null}
                    </div>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </>
      ) : null}

      {recommendationSets.items.length > 0 ? (
        <>
          <h3>추천 이력</h3>
          <ul className="saved-list">
            {recommendationSets.items.map((set) => (
              <li key={set.id} className="saved-item">
                {set.items.map((item) => (
                  <LottoBalls key={item.position} numbers={item.numbers} />
                ))}
                <div className="saved-item-row">
                  <p className="muted">
                    <time dateTime={set.createdAt}>{formatDateTime(set.createdAt)}</time>
                  </p>
                  <button
                    type="button"
                    className="saved-delete-btn"
                    onClick={() => setPendingDelete({ kind: "recommendation", id: set.id })}
                    aria-label={`${formatDateTime(set.createdAt)} 추천 세트 삭제`}
                  >
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>
          {recommendationSets.page + 1 < recommendationSets.totalPages ? (
            <div className="recommend-history-more">
              <button type="button" className="button secondary" onClick={loadMore} disabled={isLoadingMore}>
                {isLoadingMore ? "불러오는 중…" : "더 보기"}
              </button>
              <p className="muted" role="status" aria-live="polite">
                전체 {recommendationSets.totalElements}건 중 {recommendationSets.items.length}건 표시
              </p>
            </div>
          ) : (
            recommendationSets.totalElements > 0 && (
              <p className="muted" role="status" aria-live="polite">
                전체 {recommendationSets.totalElements}건을 모두 표시했습니다.
              </p>
            )
          )}
        </>
      ) : null}

      <ConfirmDialog
        open={pendingDelete !== null}
        title={pendingDelete?.kind === "saved" ? "저장 번호를 삭제할까요?" : "추천 세트를 삭제할까요?"}
        description="삭제한 기록은 되돌릴 수 없습니다."
        confirmLabel="삭제"
        pending={deleting}
        errorMessage={deleteError ?? undefined}
        onConfirm={confirmDelete}
        onCancel={() => {
          setPendingDelete(null);
          setDeleteError(null);
        }}
      />
    </section>
  );
}
