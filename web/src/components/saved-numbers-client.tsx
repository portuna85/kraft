"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { getDeviceToken } from "@/lib/device-token";
import { browserFetch, BrowserApiError } from "@/lib/browser-api";
import { EmptyState } from "@/ui/primitives/empty-state";
import { ErrorState } from "@/ui/primitives/error-state";
import { ConfirmDialog } from "@/ui/primitives/confirm-dialog";
import { useCommunitySession } from "@/lib/community-session-provider";
import { deleteMySavedNumber, getMySavedNumberMatches, getMySavedNumbers } from "@/lib/community-client";

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

type Props = {
  latestRound: number;
};

export function SavedNumbersClient({ latestRound }: Props) {
  const { session, claimStatus } = useCommunitySession();
  // C-2: 로그인 상태면 계정(소유자) 스코프로 읽고 쓴다 — claim이 진행 중일 때는 아직
  // 기다린다(귀속이 옮기는 도중의 데이터를 읽지 않기 위해). claimStatus가 settled/error로
  // 넘어가면(비로그인도 이 훅에서는 항상 settled·idle) 아래 effect가 다시 읽어온다.
  const isClaimSettling = claimStatus === "claiming";
  const useOwnerScope = Boolean(session?.loggedIn) && !isClaimSettling;

  const [items, setItems] = useState<SavedNumber[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [message, setMessage] = useState("");
  const [selectedRound, setSelectedRound] = useState<string>("latest");
  const [customRoundInput, setCustomRoundInput] = useState("");
  const [matchMap, setMatchMap] = useState<Map<number, SavedNumberMatchResult>>(new Map());
  const [matchState, setMatchState] = useState<MatchState>("idle");
  const matchFetchSeqRef = useRef(0);
  // FE-003: 삭제 확인 대상. null이면 다이얼로그가 닫힌 상태다.
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const loadSavedNumbers = useCallback(() => {
    if (isClaimSettling) {
      // 귀속이 진행 중이면 어느 쪽 목록을 보여줘도 곧 stale해진다 — claimStatus가
      // settled로 바뀌는 순간 이 effect가 다시 실행되어 최종 상태를 가져온다.
      return;
    }
    const request = useOwnerScope
      ? getMySavedNumbers()
      : browserFetch<SavedNumber[]>("/api/v1/saved", { headers: { "X-Device-Token": getDeviceToken() } });
    request
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
  }, [isClaimSettling, useOwnerScope]);

  useEffect(() => {
    loadSavedNumbers();
  }, [loadSavedNumbers]);

  function retrySavedNumbers() {
    setIsLoading(true);
    setHasError(false);
    loadSavedNumbers();
  }

  const fetchMatches = useCallback(() => {
    if (items.length === 0 || isClaimSettling) {
      return;
    }

    const seq = ++matchFetchSeqRef.current;

    const request = useOwnerScope
      ? getMySavedNumberMatches(selectedRound)
      : browserFetch<SavedNumberMatchResult[]>(
          `/api/v1/saved/matches?round=${encodeURIComponent(selectedRound)}`,
          { headers: { "X-Device-Token": getDeviceToken() } },
        );
    request
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
  }, [items, selectedRound, isClaimSettling, useOwnerScope]);

  useEffect(() => {
    fetchMatches();
  }, [fetchMatches]);

  // FE-003: 확인 방식을 다이얼로그로 통일하면서 5초 유예 삭제를 걷어냈다. 유예 방식은
  // 타이머·대기 상태·이탈 시 flush(FE-045)까지 따라붙는데, 확인이 선행되면 그 복잡도가
  // 통째로 사라진다. 삭제는 확인 직후 한 번만 일어난다.
  async function confirmDelete() {
    if (pendingDeleteId === null) return;
    const id = pendingDeleteId;
    setDeleting(true);
    setDeleteError(null);
    try {
      if (useOwnerScope) {
        await deleteMySavedNumber(id);
      } else {
        await browserFetch(`/api/v1/saved/${id}`, {
          method: "DELETE",
          headers: { "X-Device-Token": getDeviceToken() },
        });
      }
      setItems((prev) => prev.filter((x) => x.id !== id));
      setPendingDeleteId(null);
      setMessage("저장 번호를 삭제했습니다.");
    } catch (err) {
      // 삭제 실패 — 항목은 그대로 두고 다이얼로그 안에서 이유를 알린다.
      if (!(err instanceof BrowserApiError || err instanceof Error)) throw err;
      setDeleteError("저장 번호를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setDeleting(false);
    }
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
        <div className="saved-empty-state">
          <ErrorState
            title="저장 번호를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
            retry={{ label: "다시 시도", onClick: retrySavedNumbers }}
          />
        </div>
      ) : items.length === 0 ? (
        <div className="saved-empty-state">
          <EmptyState title="아직 저장한 번호가 없습니다. 추천 페이지에서 조합을 저장해 보세요." />
        </div>
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
                <ErrorState
                  variant="inline"
                  title="대조 결과를 불러오지 못했습니다."
                  retry={{ label: "다시 시도", onClick: fetchMatches }}
                />
              ) : null}
            </div>
          ) : (
            // FE-042: 최신 회차 조회(SSR)가 실패하면 latestRound가 0이 되어 회차 컨트롤이
            // 통째로 사라졌다. 저장 목록은 보이는데 대조 기능만 이유 없이 없어져,
            // 원래 없는 기능인지 고장인지 알 수 없었다.
            <ErrorState
              variant="inline"
              title="회차 정보를 불러오지 못해 당첨 대조를 할 수 없습니다."
              description="저장한 번호는 그대로 확인할 수 있습니다."
              retry={{ label: "새로고침", onClick: () => window.location.reload() }}
            />
          )}

          <ul className="saved-list">
            {items.map((item) => {
              const match = matchMap.get(item.id);
              return (
                <li key={item.id} className="saved-item">
                  <div className="saved-item-row">
                    <LottoBalls numbers={item.numbers} />
                    <button
                      type="button"
                      className="saved-delete-btn"
                      onClick={() => setPendingDeleteId(item.id)}
                      aria-label={`${item.numbers.join(", ")} 조합 삭제`}
                    >
                      삭제
                    </button>
                  </div>
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
      )}

      <ConfirmDialog
        open={pendingDeleteId !== null}
        title="저장 번호를 삭제할까요?"
        description="삭제한 저장 번호는 되돌릴 수 없습니다."
        confirmLabel="삭제"
        pending={deleting}
        errorMessage={deleteError ?? undefined}
        onConfirm={confirmDelete}
        onCancel={() => {
          setPendingDeleteId(null);
          setDeleteError(null);
        }}
      />
    </div>
  );
}
