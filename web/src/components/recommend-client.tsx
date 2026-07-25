"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { LottoBalls } from "@/components/lotto-balls";
import { getDeviceToken } from "@/lib/device-token";
import { parseExcludedNumbers } from "@/lib/lotto-validation";
import type { RecommendationResponse } from "@/lib/api";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";

const DEFAULT_COUNT = 5;
const DEFAULT_MAXIMIZE_PRIZE = true;

const TEXT = {
  countLabel: "조합 수",
  excludedLabel: "제외 번호",
  excludedPlaceholder: "예: 1, 2, 3",
  maximizePrizeLabel: "공동 당첨 분산형 추천",
  maximizePrizeHint: "과거 1등 조합을 제외하고 상대적으로 덜 겹치는 조합을 우선 선택합니다.",
  submit: "추천받기",
  pending: "생성 중...",
  disclaimer:
    "모든 6개 번호 조합의 1등 당첨 확률은 동일합니다. “공동 당첨 분산형 추천”은 공동 당첨 가능성을 낮추는 선택일 뿐 확률을 높이지 않습니다.",
  detailLink: "자세히 보기",
  recommendPrefix: "추천",
  save: "저장",
  saving: "저장 중...",
  saved: "저장됨",
  savedCreated: "저장했습니다.",
  savedExists: "이미 저장한 조합입니다.",
  saveFailed: "저장하지 못했습니다.",
  generateFailed: "추천 생성에 실패했습니다.",
  loadFailed: "추천 결과를 불러오지 못했습니다.",
  ignoredPrefix: "무시된 입력값:",
  saveLabelPrefix: "추천 조합",
} as const;

export function RecommendClient() {
  const [count, setCount] = useState(String(DEFAULT_COUNT));
  const [excluded, setExcluded] = useState("");
  const [maximizePrize, setMaximizePrize] = useState(DEFAULT_MAXIMIZE_PRIZE);
  const [recommendations, setRecommendations] = useState<number[][]>([]);
  const [message, setMessage] = useState("");
  const [savingIndexes, setSavingIndexes] = useState<Set<number>>(new Set());
  const [savedIndexes, setSavedIndexes] = useState<Set<number>>(new Set());
  const [isPending, setIsPending] = useState(true);
  const fetchSeqRef = useRef(0);

  async function fetchRecommendations(
    reqCount: number,
    reqExcluded: number[],
    reqMaximizePrize: boolean,
    initialMessage = "",
  ) {
    const seq = ++fetchSeqRef.current;
    setMessage(initialMessage);
    setIsPending(true);
    setSavedIndexes(new Set());
    setSavingIndexes(new Set());

    try {
      const payload = await browserFetch<RecommendationResponse>("/api/v1/numbers/recommend", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          count: reqCount,
          excludedNumbers: reqExcluded,
          reduceSharedWinnerRisk: reqMaximizePrize,
        }),
      });
      if (seq !== fetchSeqRef.current) return;
      setRecommendations(payload.recommendations);
    } catch (err) {
      if (seq !== fetchSeqRef.current) return;
      if (err instanceof BrowserApiError) {
        setMessage(err.message || TEXT.generateFailed);
      } else {
        setMessage(TEXT.loadFailed);
      }
    } finally {
      if (seq === fetchSeqRef.current) {
        setIsPending(false);
      }
    }
  }

  useEffect(() => {
    const seq = ++fetchSeqRef.current;

    async function loadInitialRecommendations() {
      try {
        const payload = await browserFetch<RecommendationResponse>("/api/v1/numbers/recommend", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            count: DEFAULT_COUNT,
            excludedNumbers: [],
            reduceSharedWinnerRisk: DEFAULT_MAXIMIZE_PRIZE,
          }),
        });
        if (seq !== fetchSeqRef.current) return;
        setRecommendations(payload.recommendations);
      } catch (err) {
        if (seq !== fetchSeqRef.current) return;
        if (err instanceof BrowserApiError) {
          setMessage(err.message || TEXT.generateFailed);
        } else {
          setMessage(TEXT.loadFailed);
        }
      } finally {
        if (seq === fetchSeqRef.current) {
          setIsPending(false);
        }
      }
    }

    void loadInitialRecommendations();
  }, []);

  async function handleRecommend(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const { valid: excludedNumbers, ignored } = parseExcludedNumbers(excluded);
    const ignoredMessage =
      ignored.length > 0 ? `${TEXT.ignoredPrefix} ${ignored.join(", ")}` : "";
    await fetchRecommendations(Number(count), excludedNumbers, maximizePrize, ignoredMessage);
  }

  async function handleSave(numbers: number[], index: number) {
    setSavingIndexes((previous) => new Set(previous).add(index));
    setMessage("");

    try {
      const payload = await browserFetch<{ created?: boolean }>("/api/v1/saved", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Device-Token": getDeviceToken(),
        },
        body: JSON.stringify({
          numbers,
          label: `${TEXT.saveLabelPrefix} ${index + 1}`,
          source: "RECOMMEND",
        }),
      });
      setSavedIndexes((previous) => new Set(previous).add(index));
      setMessage(payload.created ? TEXT.savedCreated : TEXT.savedExists);
    } catch (err) {
      setMessage(
        err instanceof BrowserApiError && err.message ? err.message : TEXT.saveFailed,
      );
    } finally {
      setSavingIndexes((previous) => {
        const next = new Set(previous);
        next.delete(index);
        return next;
      });
    }
  }

  return (
    <div className="recommend-layout">
      <form className="recommend-form" onSubmit={handleRecommend}>
        <label>
          {TEXT.countLabel}
          <input
            type="number"
            inputMode="numeric"
            min="1"
            max="10"
            value={count}
            onChange={(event) => setCount(event.target.value)}
          />
        </label>
        <label>
          {TEXT.excludedLabel}
          <input
            type="text"
            value={excluded}
            onChange={(event) => setExcluded(event.target.value)}
            placeholder={TEXT.excludedPlaceholder}
          />
        </label>
        <label className="recommend-toggle">
          <span className="recommend-toggle-control">
            <input
              type="checkbox"
              checked={maximizePrize}
              onChange={(event) => setMaximizePrize(event.target.checked)}
            />
            <span className="recommend-toggle-label">{TEXT.maximizePrizeLabel}</span>
          </span>
          <span className="recommend-toggle-hint">{TEXT.maximizePrizeHint}</span>
        </label>
        <button type="submit" disabled={isPending}>
          {isPending ? TEXT.pending : TEXT.submit}
        </button>
        <p className="muted recommend-disclaimer">
          {TEXT.disclaimer} <Link href="/info/faq">{TEXT.detailLink}</Link>
        </p>
      </form>

      {message ? (
        <p className="status-text" role="status" aria-live="polite">
          {message}
        </p>
      ) : null}

      {recommendations.length > 0 && (
        <div className="recommend-grid">
          {recommendations.map((numbers, index) => (
            <article key={`${numbers.join("-")}-${index}`} className="recommend-card">
              <div className="recommend-card-row">
                <div className="recommend-card-info">
                  <p className="eyebrow">
                    {TEXT.recommendPrefix} {index + 1}
                  </p>
                  <LottoBalls numbers={numbers} />
                </div>
                <button
                  type="button"
                  onClick={() => handleSave(numbers, index)}
                  disabled={savingIndexes.has(index) || savedIndexes.has(index)}
                  className={`recommend-save-btn${savedIndexes.has(index) ? " saved" : ""}`}
                >
                  {savedIndexes.has(index)
                    ? TEXT.saved
                    : savingIndexes.has(index)
                      ? TEXT.saving
                      : TEXT.save}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
