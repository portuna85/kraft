import { useEffect, useRef, useState } from "react";
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
  // 마운트 시 최초 요청이 곧바로 나가므로 기본값부터 pending이다 — 이렇게 하면 초기
  // 이펙트에서 별도로 setIsPending(true)를 동기 호출할 필요가 없다(react-hooks/set-state-in-effect).
  const [isPending, setIsPending] = useState(true);
  const [meta, setMeta] = useState<{ strategy: Strategy; algorithmVersion: string; historyThroughRound: number } | null>(
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

  // 마운트 시 최초 조회는 이펙트 안에서 프로미스 체인(.then/.catch/.finally)으로 직접
  // 처리한다 — 별도의 async 함수를 이펙트가 동기적으로 호출하면(예: void fetchX())
  // "effect 안에서 setState를 동기 호출"로 오탐하는 린트 규칙(react-hooks/set-state-in-effect)에
  // 걸린다. 콜백 안의 setState는 프로미스가 실제로 resolve된 뒤에만 실행되므로 문제 없다.
  useEffect(() => {
    const seq = ++fetchSeqRef.current;
    browserFetch<RecommendationResponse>("/api/v1/numbers/recommend", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Device-Token": getDeviceToken(),
      },
      body: requestBody(),
    })
      .then((payload) => applyResponse(seq, payload))
      .catch((err) => {
        if (seq !== fetchSeqRef.current) return;
        setMessage(err instanceof BrowserApiError && err.message ? err.message : TEXT.generateFailed);
      })
      .finally(() => {
        if (seq === fetchSeqRef.current) setIsPending(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
