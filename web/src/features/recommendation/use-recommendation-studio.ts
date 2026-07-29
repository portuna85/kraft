import { useRef, useState } from "react";
import { getDeviceToken } from "@/lib/device-token";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";
import type { RecommendationItem, RecommendationResponse, Strategy } from "./types";
import { MIN_COUNT, MAX_COUNT } from "./types";

const TEXT = {
  generateFailed: "추천 생성에 실패했습니다.",
  loadFailed: "추천 결과를 불러오지 못했습니다.",
  saveFailed: "저장하지 못했습니다.",
  savedCreated: "저장했습니다.",
  savedExists: "이미 저장한 조합입니다.",
  saveLabelPrefix: "추천 조합",
} as const;

export type UseRecommendationStudioOptions = {
  initialCount?: number;
  initialStrategy?: Strategy;
};

/**
 * 홈 즉시추천과 추천 스튜디오가 공유하는 상태 훅. 전략·고정/제외 번호·조합 수를 들고 있다가
 * 요청 시 X-Device-Token을 함께 보내 서버가 recommendation_sets에 영속화하도록 한다
 * (백엔드는 헤더가 없으면 stateless로 응답 — 기존 호환 클라이언트와 동일하게 동작).
 */
export function useRecommendationStudio(options: UseRecommendationStudioOptions = {}) {
  const [strategy, setStrategy] = useState<Strategy>(options.initialStrategy ?? "random");
  const [count, setCount] = useState(options.initialCount ?? 5);
  const [locked, setLocked] = useState<number[]>([]);
  const [excluded, setExcluded] = useState<number[]>([]);
  const [items, setItems] = useState<RecommendationItem[]>([]);
  const [message, setMessage] = useState("");
  // F-P0-6/7: 이전에는 마운트 시 곧바로 POST가 나가 방문마다 recommendation_sets에 영속
  // 기록이 쌓였다 — 사용자가 명시적으로 생성 버튼을 눌러야만 요청이 나가도록 idle에서 시작한다.
  const [isPending, setIsPending] = useState(false);
  const [meta, setMeta] = useState<{
    strategy: Strategy;
    algorithmVersion: string;
    historyThroughRound: number;
    historicalExclusionApplied: boolean;
    exclusionPolicyVersion: string | null;
  } | null>(
    null,
  );
  const [savingPositions, setSavingPositions] = useState<Set<number>>(new Set());
  const [savedPositions, setSavedPositions] = useState<Set<number>>(new Set());
  const fetchSeqRef = useRef(0);

  function applyResponse(seq: number, payload: RecommendationResponse) {
    if (seq !== fetchSeqRef.current) return;
    setItems(
      payload.items ??
        payload.recommendations.map((numbers, index) => ({
          position: index + 1,
          numbers,
          score: null,
          explanationCodes: [],
        })),
    );
    setMeta({
      strategy: payload.strategy,
      algorithmVersion: payload.algorithmVersion,
      historyThroughRound: payload.historyThroughRound,
      historicalExclusionApplied: payload.historicalExclusionApplied ?? true,
      exclusionPolicyVersion: payload.exclusionPolicyVersion ?? null,
    });
  }

  function requestBody() {
    return JSON.stringify({
      count: Math.min(MAX_COUNT, Math.max(MIN_COUNT, count)),
      excludedNumbers: excluded,
      lockedNumbers: locked,
      strategy,
    });
  }

  /** 사용자 조작(제출 버튼 등)으로 호출하는 공개 API — 이전 결과·메시지를 리셋한 뒤 재조회한다. */
  async function generate() {
    const seq = ++fetchSeqRef.current;
    setMessage("");
    setIsPending(true);
    setSavedPositions(new Set());
    setSavingPositions(new Set());

    try {
      const payload = await browserFetch<RecommendationResponse>("/api/v1/numbers/recommend", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Device-Token": getDeviceToken(),
        },
        body: requestBody(),
      });
      applyResponse(seq, payload);
    } catch (err) {
      if (seq !== fetchSeqRef.current) return;
      setMessage(err instanceof BrowserApiError && err.message ? err.message : TEXT.generateFailed);
    } finally {
      if (seq === fetchSeqRef.current) setIsPending(false);
    }
  }

  async function save(numbers: number[], position: number) {
    setSavingPositions((prev) => new Set(prev).add(position));
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
          label: `${TEXT.saveLabelPrefix} ${position}`,
          source: "RECOMMEND",
        }),
      });
      setSavedPositions((prev) => new Set(prev).add(position));
      setMessage(payload.created ? TEXT.savedCreated : TEXT.savedExists);
    } catch (err) {
      setMessage(err instanceof BrowserApiError && err.message ? err.message : TEXT.saveFailed);
    } finally {
      setSavingPositions((prev) => {
        const next = new Set(prev);
        next.delete(position);
        return next;
      });
    }
  }

  return {
    strategy,
    setStrategy,
    count,
    setCount,
    locked,
    excluded,
    setLockedAndExcluded: (nextLocked: number[], nextExcluded: number[]) => {
      setLocked(nextLocked);
      setExcluded(nextExcluded);
    },
    items,
    meta,
    message,
    isPending,
    savingPositions,
    savedPositions,
    generate,
    save,
  };
}
