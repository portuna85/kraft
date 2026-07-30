"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { getDeviceToken } from "@/lib/device-token";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";

type SavedNumber = {
  id: number;
  numbers: number[];
  label: string | null;
  source: string;
  createdAt: string;
};

type SavedNumberMatchResult = {
  savedNumber: SavedNumber;
  round: number;
  drawDate: string;
  drawNumbers: number[];
  bonusNumber: number;
  matchedCount: number;
  bonusMatch: boolean;
  prizeTier: string;
};

type MatchState = "idle" | "loading" | "success" | "error";

function isWin(prizeTier: string): boolean {
  return prizeTier !== "낙첨";
}

const RECENT_ROUND_OPTIONS = 20;
const DELETE_UNDO_MS = 5000;

type Props = {
  latestRound: number;
};

export function SavedNumbersClient({ latestRound }: Props) {
  const [items, setItems] = useState<SavedNumber[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [message, setMessage] = useState("");
  const [selectedRound, setSelectedRound] = useState<string>("latest");
  const [customRoundInput, setCustomRoundInput] = useState("");
  const [matchMap, setMatchMap] = useState<Map<number, SavedNumberMatchResult>>(new Map());
  const [matchState, setMatchState] = useState<MatchState>("idle");
  const matchFetchSeqRef = useRef(0);
  // R-44: 즉시 삭제 대신 5초 유예 후 실제 삭제 — 모바일에서 확인 dialog보다 흐름을
  // 덜 끊으면서도 실수로 지운 항목을 되돌릴 수 있게 한다.
  const [pendingDeleteIds, setPendingDeleteIds] = useState<Set<number>>(new Set());
  const deleteTimers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  useEffect(() => {
    const timers = deleteTimers.current;
    return () => {
      timers.forEach((timer) => clearTimeout(timer));
    };
  }, []);

  useEffect(() => {
    browserFetch<SavedNumber[]>("/api/v1/saved", {
      headers: { "X-Device-Token": getDeviceToken() },
    })
      .then((savedItems) => {
        setItems(savedItems);
        setHasError(false);
      })
      .catch(() => {
        setHasError(true);
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  const fetchMatches = useCallback(() => {
    if (items.length === 0) {
      return;
    }

    const seq = ++matchFetchSeqRef.current;

    browserFetch<SavedNumberMatchResult[]>(
      `/api/v1/saved/matches?round=${encodeURIComponent(selectedRound)}`,
      { headers: { "X-Device-Token": getDeviceToken() } },
    )
      .then((results) => {
        if (seq !== matchFetchSeqRef.current) return;
        const map = new Map<number, SavedNumberMatchResult>();
        for (const result of results) {
          map.set(result.savedNumber.id, result);
        }
        setMatchMap(map);
        setMatchState("success");
      })
      .catch(() => {
        if (seq !== matchFetchSeqRef.current) return;
        // 실패해도 이전에 성공한 matchMap은 그대로 유지 — "불러오기 실패"가
        // "대조 결과 없음"처럼 보이지 않게 한다.
        setMatchState("error");
      });
  }, [items, selectedRound]);

  useEffect(() => {
    fetchMatches();
  }, [fetchMatches]);

  function scheduleDelete(item: SavedNumber) {
    setPendingDeleteIds((prev) => new Set(prev).add(item.id));
    const timer = setTimeout(async () => {
      deleteTimers.current.delete(item.id);
      try {
        await browserFetch(`/api/v1/saved/${item.id}`, {
          method: "DELETE",
          headers: { "X-Device-Token": getDeviceToken() },
        });
        setItems((prev) => prev.filter((x) => x.id !== item.id));
      } catch (err) {
        // 삭제 실패 — 대기 상태만 풀고 항목은 그대로 유지한다.
        if (!(err instanceof BrowserApiError || err instanceof Error)) throw err;
        setMessage("저장 번호를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
      } finally {
        setPendingDeleteIds((prev) => {
          const next = new Set(prev);
          next.delete(item.id);
          return next;
        });
      }
    }, DELETE_UNDO_MS);
    deleteTimers.current.set(item.id, timer);
  }

  function cancelDelete(id: number) {
    const timer = deleteTimers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      deleteTimers.current.delete(id);
    }
    setPendingDeleteIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  }

  // 회차 변경 즉시(사용자 조작 시점에) 이전 회차의 대조 결과를 비우고 로딩 상태로
  // 전환한다(P1-06) — 그러지 않으면 새 회차를 fetch하는 동안 이전 회차의 당첨 배지가
  // 그대로 보여서 사용자가 새 회차 결과로 오인할 수 있다.
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

  const recentRoundOptions: number[] = [];
  for (let round = latestRound; round >= 1 && recentRoundOptions.length < RECENT_ROUND_OPTIONS; round--) {
    recentRoundOptions.push(round);
  }

  return (
    <div className="saved-layout">
      {message ? <p className="status-text" role="status" aria-live="polite">{message}</p> : null}
      {isLoading ? (
        <p className="saved-empty-state">저장된 번호를 불러오는 중입니다.</p>
      ) : hasError ? (
        <p className="saved-empty-state">저장 번호를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
      ) : items.length === 0 ? (
        <p className="saved-empty-state">아직 저장한 번호가 없습니다. 추천 페이지에서 조합을 저장해 보세요.</p>
      ) : (
        <>
          {latestRound > 0 ? (
            <div className="saved-round-controls">
              <label className="saved-round-selector">
                대조할 회차
                <select
                  value={selectedRound}
                  onChange={(event) => changeSelectedRound(event.target.value)}
                >
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
                <p className="saved-match-error" aria-live="polite">
                  대조 결과를 불러오지 못했습니다.{" "}
                  <button type="button" className="button secondary" onClick={fetchMatches}>
                    다시 시도
                  </button>
                </p>
              ) : null}
            </div>
          ) : null}

          <ul className="saved-list">
            {items.map((item) => {
              const match = matchMap.get(item.id);
              const isPending = pendingDeleteIds.has(item.id);
              return (
                <li
                  key={item.id}
                  className={`saved-item${isPending ? " is-pending-delete" : ""}`}
                >
                  <div className="saved-item-row">
                    <LottoBalls numbers={item.numbers} />
                    {isPending ? (
                      <button
                        type="button"
                        className="button secondary saved-undo-btn"
                        onClick={() => cancelDelete(item.id)}
                      >
                        실행 취소
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="saved-delete-btn"
                        onClick={() => scheduleDelete(item)}
                        aria-label={`${item.numbers.join(", ")} 조합 삭제`}
                      >
                        삭제
                      </button>
                    )}
                  </div>
                  {!isPending && match ? (
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
      )}
    </div>
  );
}
