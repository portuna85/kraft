"use client";

import type { MouseEvent } from "react";

import { useRovingGrid } from "@/shared/hooks/use-roving-grid";

import styles from "./number-grid.module.css";

export type NumberMark = "none" | "locked" | "excluded";

const NUMBERS = Array.from({ length: 45 }, (_, index) => index + 1);
const COLUMNS = 7;

const MARK_LABEL: Record<NumberMark, string> = {
  none: "",
  locked: "고정됨",
  excluded: "제외됨",
};

/**
 * 1~45 번호판 — improvement_fe.md §9.4, §6.3 M-5, §19.2 P-2
 *
 * **이벤트 위임 하나로 45개를 처리한다.** 셀마다 핸들러를 붙이면 이 그리드가 있는
 * 라우트마다 45개 클로저를 하이드레이션해야 한다. 세 라우트가 이 판을 쓰므로 비용이
 * 세 배로 든다. 클릭 대상의 data-number만 읽으면 핸들러는 하나면 된다.
 *
 * 키보드는 로빙 그리드다 — 45개를 전부 탭 순서에 넣으면 판 하나를 지나가는 데
 * Tab을 45번 눌러야 한다.
 */
export function NumberGrid({
  marks,
  onToggle,
  "aria-label": label,
}: {
  marks: ReadonlyMap<number, NumberMark>;
  onToggle: (value: number) => void;
  "aria-label": string;
}) {
  const { activeIndex, onKeyDown, getCellProps } = useRovingGrid({
    itemCount: NUMBERS.length,
    columns: COLUMNS,
  });

  function onClick(event: MouseEvent<HTMLDivElement>) {
    const target = (event.target as HTMLElement).closest<HTMLElement>("[data-number]");
    const raw = target?.dataset.number;
    if (raw === undefined) return;
    onToggle(Number(raw));
  }

  return (
    <div
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

        return (
          <button
            key={value}
            type="button"
            data-number={value}
            className={`${styles.cell} ${mark === "locked" ? styles.locked : ""} ${
              mark === "excluded" ? styles.excluded : ""
            }`}
            // 상태를 색과 취소선으로만 전달하지 않는다.
            aria-label={markLabel === "" ? `${value}번` : `${value}번 ${markLabel}`}
            aria-pressed={mark !== "none"}
            tabIndex={cellProps.tabIndex}
            onFocus={cellProps.onFocus}
            data-active={index === activeIndex || undefined}
          >
            {value}
          </button>
        );
      })}
    </div>
  );
}
