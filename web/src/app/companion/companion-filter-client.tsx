"use client";

import { useRef, useState } from "react";
import { useRovingGrid } from "@/ui/primitives/use-roving-grid";
import { ballColorClass } from "@/lib/ball-color";
import type { CompanionPair, CompanionStatsResponse } from "@/lib/api";
import { browserFetch } from "@/lib/browser-api";
import { EmptyState } from "@/ui/primitives/empty-state";
import { ErrorState } from "@/ui/primitives/error-state";
import styles from "./companion.module.css";

type Props = {
  pairs: CompanionPair[];
  totalRounds: number;
};

type FilterState =
  | { status: "idle" }
  | { status: "loading"; ball: number }
  | { status: "success"; ball: number; pairs: CompanionPair[] }
  | { status: "error"; ball: number };

const BALL_COUNT = 45;

export function CompanionFilterClient({ pairs, totalRounds }: Props) {
  const [selected, setSelected] = useState<number | null>(null);
  const [filterState, setFilterState] = useState<FilterState>({ status: "idle" });
  const fetchSeqRef = useRef(0);
  // FE-017/FE-107: 여기 있던 로빙 tabindex 구현을 useRovingGrid로 추출해
  // /recommend의 NumberBoard와 공유한다. 열 수 실측 방식은 그대로다.
  const roving = useRovingGrid(BALL_COUNT);

  function fetchPairsForBall(ball: number) {
    const seq = ++fetchSeqRef.current;
    setFilterState({ status: "loading", ball });

    browserFetch<CompanionStatsResponse>(`/api/v1/stats/companion?ball=${ball}`)
      .then((data) => {
        if (seq !== fetchSeqRef.current) return;
        setFilterState({ status: "success", ball, pairs: data.topPairs });
      })
      .catch(() => {
        if (seq !== fetchSeqRef.current) return;
        setFilterState({ status: "error", ball });
      });
  }

  function selectNumber(number: number) {
    if (selected === number) {
      fetchSeqRef.current++; // 진행 중인 요청 결과를 무시하도록 시퀀스만 올림
      setSelected(null);
      setFilterState({ status: "idle" });
      return;
    }

    setSelected(number);
    fetchPairsForBall(number);
  }

  function retry() {
    if (selected !== null) {
      fetchPairsForBall(selected);
    }
  }

  // 선택 없음: 초기에 전달받은 상위 50개만 표시(과한 목록 방지). 번호 선택: success 상태일 때만
  // 서버 필터 결과를 사용 — loading/error 동안에는 절대 "기록 없음"을 오표시하지 않는다.
  const filtered = selected === null
    ? pairs
    : filterState.status === "success"
      ? filterState.pairs
      : null;

  return (
    <>
      <div className={styles.filter}>
        <p id="companion-filter-label" className={styles.filterLabel}>번호로 필터</p>
        <div
          className={styles.filterBalls}
          role="group"
          aria-labelledby="companion-filter-label"
          onKeyDown={roving.handleKeyDown}
        >
          {Array.from({ length: BALL_COUNT }, (_, index) => index + 1).map((number, index) => (
            <button
              key={number}
              {...roving.getItemProps(index)}
              type="button"
              onClick={() => selectNumber(number)}
              className={`ball ball-sm ${ballColorClass(number)}${selected === number ? " ball-selected" : ""}`}
              aria-pressed={selected === number}
            >
              {number}
            </button>
          ))}
        </div>
        {filterState.status === "loading" && (
          <p className="muted" aria-live="polite">해당 번호의 동반 출현 데이터를 불러오는 중...</p>
        )}
        {filterState.status === "error" && (
          <ErrorState
            variant="inline"
            title="동반 출현 데이터를 불러오지 못했습니다."
            retry={{ label: "다시 시도", onClick: retry }}
          />
        )}
        {selected !== null && (
          <button
            type="button"
            className={`button secondary ${styles.filterClear}`}
            onClick={() => {
              setSelected(null);
              setFilterState({ status: "idle" });
            }}
          >
            필터 해제
          </button>
        )}
      </div>

      {/* FE-034: 빈 결과를 <li>로 넣어 "목록 항목이 아닌 내용"이 목록 항목으로 노출됐다.
          FE-009: 표현도 다른 화면과 같은 빈 상태로 맞춘다. */}
      {filtered !== null && filtered.length === 0 && (
        <EmptyState
          title="해당 번호를 포함한 동반 출현 기록이 없습니다."
          description="다른 번호를 선택하거나 필터를 해제해 보세요."
        />
      )}

      {filtered !== null && filtered.length > 0 && (
        <ol className={styles.list} data-testid="companion-list">
          {filtered.map((pair, index) => {
            const pct = totalRounds > 0
              ? ((pair.coCount / totalRounds) * 100).toFixed(1)
              : "0.0";

            return (
              <li key={`${pair.ballA}-${pair.ballB}`} className={styles.item}>
                <span className={styles.rank}>{index + 1}</span>
                <div className={styles.pairBalls}>
                  <span className={`ball ball-sm ${ballColorClass(pair.ballA)}`}>
                    {pair.ballA}
                  </span>
                  <span className={styles.pairSep}>×</span>
                  <span className={`ball ball-sm ${ballColorClass(pair.ballB)}`}>
                    {pair.ballB}
                  </span>
                </div>
                <div className={styles.pairInfo}>
                  <span className={styles.pairCount}>{pair.coCount}회 동반 출현</span>
                  <span className={styles.pairPct}>{pct}%</span>
                </div>
              </li>
            );
          })}

        </ol>
      )}
    </>
  );
}
