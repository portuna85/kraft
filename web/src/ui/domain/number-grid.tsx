"use client";

import { useRovingGrid } from "@/ui/primitives/use-roving-grid";
import styles from "./number-grid.module.css";

export type NumberCellState = {
  /** `aria-pressed` 값 — 눌린 상태인지. */
  pressed: boolean;
  /** 셀 아래에 표시하고 접근 가능한 이름에도 덧붙이는 상태 문구(예: "고정"). */
  stateText?: string;
  /** 상태별 시각 표현. 소비자의 CSS Module 클래스를 그대로 받는다. */
  className?: string;
  disabled?: boolean;
};

/**
 * 1~N 번호를 고르는 격자. 한 번의 Tab으로 진입하고 방향키로 이동한다.
 *
 * FE-037: `/recommend`의 고정·제외 번호판과 `/analysis`의 번호 선택이 같은 격자를
 * 필요로 하는데, 전자는 features/recommendation에 있었다. 격자 자체는 특정 기능이
 * 아니라 로또 도메인 표시 요소이므로 LottoBalls 옆(ui/domain)에 둔다 — 이렇게 해야
 * analysis가 recommendation 기능에 의존하지 않는다.
 *
 * 상태 색(고정/제외 등)은 소비자마다 다르므로 `className`으로 주입받고, 여기서는
 * 배치·터치 타깃·키보드 이동만 책임진다.
 */
export function NumberGrid({
  count,
  ariaLabel,
  getState,
  onSelect,
}: {
  count: number;
  ariaLabel: string;
  getState: (n: number) => NumberCellState;
  onSelect: (n: number) => void;
}) {
  const roving = useRovingGrid(count);

  return (
    <div className={styles.grid} role="group" aria-label={ariaLabel} onKeyDown={roving.handleKeyDown}>
      {Array.from({ length: count }, (_, index) => {
        const n = index + 1;
        const state = getState(n);
        return (
          <button
            key={n}
            type="button"
            {...roving.getItemProps(index)}
            className={`${styles.cell} ${state.className ?? ""}`}
            onClick={() => onSelect(n)}
            disabled={state.disabled}
            aria-label={state.stateText ? `번호 ${n}, ${state.stateText}` : `번호 ${n}`}
            aria-pressed={state.pressed}
          >
            <span>{n}</span>
            {state.stateText ? <span className={styles.stateLabel}>{state.stateText}</span> : null}
          </button>
        );
      })}
    </div>
  );
}
