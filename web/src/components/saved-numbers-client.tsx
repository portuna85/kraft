"use client";

import { formatDateTime } from "@/lib/format";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import { EmptyState } from "@/ui/primitives/empty-state";
import { ErrorState } from "@/ui/primitives/error-state";
import { ConfirmDialog } from "@/ui/primitives/confirm-dialog";
import { useSavedNumbersLibrary } from "./use-saved-numbers-library";

function isWin(prizeTier: string): boolean {
  return prizeTier !== "낙첨";
}

const RECENT_ROUND_OPTIONS = 20;

type Props = {
  latestRound: number;
};

export function SavedNumbersClient({ latestRound }: Props) {
  const {
    items,
    isLoading,
    hasError,
    message,
    selectedRound,
    customRoundInput,
    setCustomRoundInput,
    matchMap,
    matchState,
    pendingDeleteId,
    setPendingDeleteId,
    deleting,
    deleteError,
    setDeleteError,
    retrySavedNumbers,
    fetchMatches,
    confirmDelete,
    changeSelectedRound,
    applyCustomRound,
  } = useSavedNumbersLibrary(latestRound);

  const recentRoundOptions: number[] = [];
  for (let round = latestRound; round >= 1 && recentRoundOptions.length < RECENT_ROUND_OPTIONS; round--) {
    recentRoundOptions.push(round);
  }

  return (
    <div className="saved-layout">
      {message ? <p className="status-text" role="status" aria-live="polite">{message}</p> : null}
      {isLoading ? (
        // L-8: 평문 문단 대신 다른 화면(recommendation-history-client 등)과 같은
        // 스켈레톤 언어를 쓴다.
        <div className="saved-empty-state" aria-busy="true" aria-label="저장된 번호를 불러오는 중">
          <span className="skeleton-line skeleton-body" />
          <span className="skeleton-line skeleton-body" />
        </div>
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
                    <p className="eyebrow">{formatDateTime(item.createdAt)}</p>
                    <button
                      type="button"
                      className="saved-delete-btn"
                      onClick={() => setPendingDeleteId(item.id)}
                      aria-label={`${item.numbers.join(", ")} 조합 삭제`}
                    >
                      삭제
                    </button>
                  </div>
                  <LottoBalls numbers={item.numbers} />
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
