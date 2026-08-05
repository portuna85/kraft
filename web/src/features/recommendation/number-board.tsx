"use client";

import { NumberGrid } from "@/ui/domain/number-grid";
import { MAX_LOCKED_NUMBERS, type NumberPickState } from "./types";
import styles from "./number-board.module.css";

const NUMBER_COUNT = 45;

const STATE_TEXT: Record<NumberPickState, string> = {
  none: "",
  locked: "고정",
  excluded: "제외",
};

function stateOf(n: number, locked: readonly number[], excluded: readonly number[]): NumberPickState {
  if (locked.includes(n)) return "locked";
  if (excluded.includes(n)) return "excluded";
  return "none";
}

function nextState(current: NumberPickState, lockedCount: number): NumberPickState {
  if (current === "none") {
    return lockedCount < MAX_LOCKED_NUMBERS ? "locked" : "excluded";
  }
  if (current === "locked") return "excluded";
  return "none";
}

export function NumberBoard({
  locked,
  excluded,
  onChange,
}: {
  locked: readonly number[];
  excluded: readonly number[];
  onChange: (locked: number[], excluded: number[]) => void;
}) {
  function handleClick(n: number) {
    const current = stateOf(n, locked, excluded);
    const next = nextState(current, locked.length);

    const nextLocked = locked.filter((v) => v !== n);
    const nextExcluded = excluded.filter((v) => v !== n);
    if (next === "locked") nextLocked.push(n);
    if (next === "excluded") nextExcluded.push(n);
    onChange(nextLocked, nextExcluded);
  }

  const hasSelection = locked.length > 0 || excluded.length > 0;

  return (
    <div>
      <p className={styles.legend}>
        <span>
          <span className={`${styles.legendSwatch} ${styles.cellLocked}`} aria-hidden="true" />
          고정 번호(최대 {MAX_LOCKED_NUMBERS}개) — 모든 조합에 포함됩니다
        </span>
        <span>
          <span className={`${styles.legendSwatch} ${styles.cellExcluded}`} aria-hidden="true" />
          제외 번호 — 추천 후보에서 빠집니다
        </span>
      </p>
      <p className={styles.summary} aria-live="polite">
        <span>
          고정 {locked.length}/{MAX_LOCKED_NUMBERS} · 제외 {excluded.length}개
        </span>
        {hasSelection ? (
          <button type="button" className="link-button" onClick={() => onChange([], [])}>
            모두 해제
          </button>
        ) : null}
      </p>
      <NumberGrid
        count={NUMBER_COUNT}
        ariaLabel="1부터 45까지 번호 선택판"
        onSelect={handleClick}
        getState={(n) => {
          const state = stateOf(n, locked, excluded);
          return {
            pressed: state !== "none",
            stateText: STATE_TEXT[state] || undefined,
            className: state === "locked" ? styles.cellLocked : state === "excluded" ? styles.cellExcluded : undefined,
          };
        }}
      />
    </div>
  );
}
