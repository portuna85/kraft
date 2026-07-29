"use client";

import { useEffect, useState } from "react";
import { LottoBalls } from "@/components/lotto-balls";
import { getDeviceToken } from "@/lib/device-token";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";
import type { RecommendationSetSummary } from "@/features/recommendation/types";

const STRATEGY_LABELS = {
  random: "무작위",
  balanced: "균형 조합",
  reduce_shared_winner_risk: "공동 당첨 위험 완화",
} as const;

export function RecommendationHistoryClient() {
  const [items, setItems] = useState<RecommendationSetSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [message, setMessage] = useState("");
  const [deletingIds, setDeletingIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    browserFetch<RecommendationSetSummary[]>("/api/v1/recommendation-sets", {
      headers: { "X-Device-Token": getDeviceToken() },
    })
      .then((result) => {
        setItems(result);
        setHasError(false);
      })
      .catch(() => setHasError(true))
      .finally(() => setIsLoading(false));
  }, []);

  async function handleDelete(id: number) {
    setMessage("");
    setDeletingIds((prev) => new Set(prev).add(id));
    try {
      await browserFetch(`/api/v1/recommendation-sets/${id}`, {
        method: "DELETE",
        headers: { "X-Device-Token": getDeviceToken() },
      });
      setItems((prev) => prev.filter((item) => item.id !== id));
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

  if (isLoading) {
    return <p className="saved-empty-state">추천 이력을 불러오는 중입니다.</p>;
  }
  if (hasError) {
    return <p className="saved-empty-state">추천 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>;
  }
  if (items.length === 0) {
    return <p className="saved-empty-state">아직 저장된 추천 이력이 없습니다. 추천 페이지에서 조합을 생성해 보세요.</p>;
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
            aria-label={`추천 세트 ${item.id} 삭제`}
          >
            {deletingIds.has(item.id) ? "삭제 중..." : "삭제"}
          </button>
        </li>
        ))}
      </ul>
    </>
  );
}
