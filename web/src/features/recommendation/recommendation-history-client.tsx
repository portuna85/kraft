"use client";

import { useCallback, useEffect, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { getDeviceToken } from "@/lib/device-token";
import { formatDateTime } from "@/lib/format";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";
import type { PageResponse } from "@/lib/community-api";
import type { RecommendationSetSummary } from "@/features/recommendation/types";
import { EmptyState } from "@/ui/primitives/empty-state";
import { ErrorState } from "@/ui/primitives/error-state";
import { asyncError, asyncLoading, asyncSuccess, type AsyncState } from "@/lib/async-state";

const STRATEGY_LABELS = {
  random: "무작위",
  balanced: "균형 조합",
  reduce_shared_winner_risk: "공동 당첨 위험 완화",
} as const;

const PAGE_SIZE = 20;

/** 조회 결과 한 덩어리. items만 따로 들면 페이지 정보와 어긋날 수 있다. */
type History = {
  items: RecommendationSetSummary[];
  page: number;
  totalPages: number;
  totalElements: number;
};

export function RecommendationHistoryClient() {
  // FE-036: items·isLoading·hasError·page·totalPages를 각각 들면 "로딩 중인데 실패했고
  // 데이터도 있다" 같은 조합이 타입상 가능했다. 조회 상태는 하나의 판별 유니온으로 두고,
  // 삭제·더 보기 같은 mutation 상태는 별개로 유지한다(둘은 성격이 다르다).
  const [state, setState] = useState<AsyncState<History>>(asyncLoading);
  const [message, setMessage] = useState("");
  const [deletingIds, setDeletingIds] = useState<Set<number>>(new Set());
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  const fetchPage = useCallback((targetPage: number) => {
    return browserFetch<PageResponse<RecommendationSetSummary>>(
      `/api/v1/recommendation-sets?page=${targetPage}&size=${PAGE_SIZE}`,
      { headers: { "X-Device-Token": getDeviceToken() } }
    );
  }, []);

  const loadHistory = useCallback(() => {
    fetchPage(0)
      .then((result) => {
        setState(asyncSuccess({
          items: result.items,
          page: result.page,
          totalPages: result.totalPages,
          totalElements: result.totalElements,
        }));
      })
      .catch(() => setState(asyncError));
  }, [fetchPage]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  function retryHistory() {
    setState(asyncLoading);
    loadHistory();
  }

  // FE-021: 예전에는 size=50으로 한 번만 받고 "더 보기"가 없어, 51번째부터는 접근 경로도
  // 안내도 없었다. 응답의 totalElements/totalPages를 실제로 쓴다.
  function loadMore() {
    if (state.status !== "success") return;
    const current = state.data;
    setIsLoadingMore(true);
    setMessage("");
    fetchPage(current.page + 1)
      .then((result) => {
        setState((prev) => {
          // 응답이 도착하는 사이 재조회로 상태가 바뀌었을 수 있다.
          if (prev.status !== "success") return prev;
          // 삭제로 목록이 밀려 같은 항목이 다시 올 수 있으므로 id로 중복을 제거한다.
          const seen = new Set(prev.data.items.map((item) => item.id));
          return asyncSuccess({
            items: [...prev.data.items, ...result.items.filter((item) => !seen.has(item.id))],
            page: result.page,
            totalPages: result.totalPages,
            totalElements: result.totalElements,
          });
        });
      })
      .catch(() => setMessage("다음 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."))
      .finally(() => setIsLoadingMore(false));
  }

  async function handleDelete(id: number) {
    setMessage("");
    setDeletingIds((prev) => new Set(prev).add(id));
    try {
      await browserFetch(`/api/v1/recommendation-sets/${id}`, {
        method: "DELETE",
        headers: { "X-Device-Token": getDeviceToken() },
      });
      setState((prev) =>
        prev.status === "success"
          ? asyncSuccess({
              ...prev.data,
              items: prev.data.items.filter((item) => item.id !== id),
              totalElements: Math.max(0, prev.data.totalElements - 1),
            })
          : prev
      );
    } catch (err) {
      if (!(err instanceof BrowserApiError || err instanceof Error)) throw err;
      setMessage("추천 이력을 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setDeletingIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  }

  if (state.status === "loading" || state.status === "idle") {
    // FE-004: 평문 문단 대신 다른 화면과 같은 스켈레톤 언어를 쓴다.
    return (
      <div className="saved-list" aria-busy="true" aria-label="추천 이력을 불러오는 중">
        <span className="skeleton-line skeleton-body" />
        <span className="skeleton-line skeleton-body" />
        <span className="skeleton-line skeleton-body" />
      </div>
    );
  }
  if (state.status === "error") {
    return <ErrorState title="추천 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요." retry={{ label: "다시 시도", onClick: retryHistory }} />;
  }

  const { items, page, totalPages, totalElements } = state.data;

  if (items.length === 0) {
    return <EmptyState title="아직 저장된 추천 이력이 없습니다. 추천 페이지에서 조합을 생성해 보세요." />;
  }

  return (
    <>
      {message ? <p className="status-text" role="status" aria-live="polite">{message}</p> : null}
      <ul className="saved-list">
        {items.map((item) => (
        <li key={item.id} className="saved-item">
          <p className="muted">
            {STRATEGY_LABELS[item.strategy]} · {item.algorithmVersion} · {item.historyThroughRound}회 반영
          </p>
          {/* FE-022: 이력 화면인데 "언제 만든 조합인지"가 없었다. */}
          <p className="muted">
            <time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time>
          </p>
          {item.items.map((entry) => (
            <div key={entry.position} className="saved-item-row">
              <LottoBalls numbers={entry.numbers} />
            </div>
          ))}
          <button
            type="button"
            className="saved-delete-btn"
            onClick={() => handleDelete(item.id)}
            disabled={deletingIds.has(item.id)}
            // FE-024: DB 식별자는 화면 어디에도 없어 "추천 세트 4821 삭제"로는 어떤 항목인지
            // 알 수 없었다. /saved처럼 사람이 식별할 수 있는 이름을 준다.
            aria-label={`${STRATEGY_LABELS[item.strategy]} ${formatDateTime(item.createdAt)} 추천 세트 삭제`}
          >
            {deletingIds.has(item.id) ? "삭제 중..." : "삭제"}
          </button>
        </li>
        ))}
      </ul>
      {page + 1 < totalPages ? (
        <div className="recommend-history-more">
          <button type="button" className="button secondary" onClick={loadMore} disabled={isLoadingMore}>
            {isLoadingMore ? "불러오는 중…" : "더 보기"}
          </button>
          <p className="muted" role="status" aria-live="polite">
            전체 {totalElements}건 중 {items.length}건 표시
          </p>
        </div>
      ) : (
        totalElements > 0 && (
          <p className="muted" role="status" aria-live="polite">
            전체 {totalElements}건을 모두 표시했습니다.
          </p>
        )
      )}
    </>
  );
}
