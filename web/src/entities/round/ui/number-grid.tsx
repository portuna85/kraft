"use client";

import { memo, type MouseEvent } from "react";

import { useGridColumnCount } from "@/shared/hooks/use-grid-column-count";
import { useRovingGrid } from "@/shared/hooks/use-roving-grid";

import styles from "./number-grid.module.css";

export type NumberMark = "none" | "locked" | "excluded";

const NUMBERS = Array.from({ length: 45 }, (_, index) => index + 1);
// I-14: 실제 열 수는 런타임에 useGridColumnCount가 측정한다 — 이건 측정 전(마운트
// 직후 한 프레임) 임시 기본값일 뿐이다.
const COLUMNS_FALLBACK = 7;

const MARK_LABEL: Record<NumberMark, string> = {
  none: "",
  locked: "고정됨",
  excluded: "제외됨",
};

/**
 * 1~45 번호판
 *
 * **이벤트 위임 하나로 45개를 처리한다.** 셀마다 핸들러를 붙이면 이 그리드가 있는
 * 라우트마다 45개 클로저를 하이드레이션해야 한다. 세 라우트가 이 판을 쓰므로 비용이
 * 세 배로 든다. 클릭 대상의 data-number만 읽으면 핸들러는 하나면 된다.
 *
 * 키보드는 로빙 그리드다 — 45개를 전부 탭 순서에 넣으면 판 하나를 지나가는 데
 * Tab을 45번 눌러야 한다.
 *
 * FE-PERF-03(docs/improvement.md): `memo()`로 감싼다 — 부모가 이 그리드와 무관한
 * 상태로 리렌더될 때 45개 버튼을 매번 다시 그리지 않기 위해서다. **호출부가
 * `marks`/`onToggle`/`isDisabled`를 매 렌더 새 참조로 넘기면 `memo()`는 얕은
 * 비교 비용만 추가하고 아무것도 막지 못한다** — 호출부에서 `useCallback`/`useMemo`로
 * 안정화하는 것이 이 최적화의 전제 조건이다.
 */
function NumberGridImpl({
  marks,
  onToggle,
  isDisabled,
  "aria-label": label,
}: {
  marks: ReadonlyMap<number, NumberMark>;
  onToggle: (value: number) => void;
  /** I-27: 지금 눌러도 반영되지 않는 셀(예: 고정 상한 도달) — 클릭은 여전히 훅으로
   * 전달한다(호출부가 거부 이유를 안내한다), 여기서는 시각·의미 표시만 맡는다. */
  isDisabled?: (value: number) => boolean;
  "aria-label": string;
}) {
  const [gridRef, columns] = useGridColumnCount<HTMLDivElement>(COLUMNS_FALLBACK);
  const { activeIndex, onKeyDown, getCellProps } = useRovingGrid({
    itemCount: NUMBERS.length,
    columns,
  });

  function onClick(event: MouseEvent<HTMLDivElement>) {
    const target = (event.target as HTMLElement).closest<HTMLElement>("[data-number]");
    const raw = target?.dataset.number;
    if (raw === undefined) return;
    onToggle(Number(raw));
  }

  return (
    <div
      ref={gridRef}
      className={styles.grid}
      role="group"
      aria-label={label}
      onClick={onClick}
      onKeyDown={onKeyDown}
    >
      {NUMBERS.map((value, index) => {
        const mark = marks.get(value) ?? "none";
        const markLabel = MARK_LABEL[mark];
        const cellProps = getCellProps(index);
        const disabled = isDisabled?.(value) ?? false;

        return (
          <button
            key={value}
            ref={cellProps.ref}
            type="button"
            data-number={value}
            className={`${styles.cell} ${mark === "locked" ? styles.locked : ""} ${
              mark === "excluded" ? styles.excluded : ""
            } ${disabled ? styles.disabled : ""}`}
            // 상태를 색과 취소선으로만 전달하지 않는다.
            aria-label={
              markLabel === ""
                ? `${value}번${disabled ? " (선택할 수 없음)" : ""}`
                : `${value}번 ${markLabel}`
            }
            aria-pressed={mark !== "none"}
            aria-disabled={disabled || undefined}
            tabIndex={cellProps.tabIndex}
            onFocus={cellProps.onFocus}
            data-active={index === activeIndex || undefined}
          >
            {value}
            {mark === "locked" && (
              <span className={styles.badge} aria-hidden="true">
                ★
              </span>
            )}
            {mark === "excluded" && (
              <span className={styles.badge} aria-hidden="true">
                ✕
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

export const NumberGrid = memo(NumberGridImpl);
