"use client";

import { useCallback, useState, type KeyboardEvent } from "react";

/**
 * 로빙 tabindex 그리드(NumberGrid)
 *
 * 1~45 번호판처럼 셀이 많은 그리드에서 모든 셀을 탭 순서에 넣으면 키보드 사용자가
 * 그리드 하나를 빠져나가는 데 45번 Tab을 눌러야 한다. 그리드 전체를 탭 정지점 하나로
 * 두고 내부는 화살표로 이동한다.
 *
 * 비활성 셀은 건너뛴다(레거시 M-9). 안 그러면 화살표가 도달은 하는데 아무 일도
 * 일어나지 않는 칸에 멈춰, 사용자는 키보드가 고장 난 것으로 받아들인다.
 *
 * 이 로직을 컴포넌트에 인라인하지 않는 이유는 레거시에서 같은 구현이 두 곳에 복제돼
 * 미묘하게 갈라졌기 때문이다(FE-017·FE-107).
 */
export type RovingGridOptions = {
  itemCount: number;
  columns: number;
  /** 비활성 셀 판정. 없으면 전부 활성으로 본다. */
  isDisabled?: (index: number) => boolean;
};

function clamp(value: number, max: number): number {
  return Math.min(Math.max(value, 0), max);
}

export function useRovingGrid({ itemCount, columns, isDisabled }: RovingGridOptions) {
  const [activeIndex, setActiveIndex] = useState(0);

  const nextEnabled = useCallback(
    (from: number, step: number): number => {
      if (itemCount === 0) return 0;
      let index = from;
      // 최대 itemCount번만 건너뛴다 — 전부 비활성이면 제자리에 머문다(무한 루프 방지).
      for (let hop = 0; hop < itemCount; hop += 1) {
        index = clamp(index, itemCount - 1);
        if (!isDisabled?.(index)) return index;
        index += step === 0 ? 1 : step;
        if (index < 0 || index > itemCount - 1) return from;
      }
      return from;
    },
    [itemCount, isDisabled],
  );

  const onKeyDown = useCallback(
    (event: KeyboardEvent<HTMLElement>) => {
      const moves: Record<string, number> = {
        ArrowRight: 1,
        ArrowLeft: -1,
        ArrowDown: columns,
        ArrowUp: -columns,
      };

      if (event.key === "Home" || event.key === "End") {
        event.preventDefault();
        const target = event.key === "Home" ? 0 : itemCount - 1;
        setActiveIndex(nextEnabled(target, event.key === "Home" ? 1 : -1));
        return;
      }

      const step = moves[event.key];
      if (step === undefined) return;

      event.preventDefault();
      setActiveIndex((current) => {
        const target = current + step;
        // 그리드 밖으로 나가는 이동은 무시한다 — 줄바꿈 이동은 혼란스럽다.
        if (target < 0 || target > itemCount - 1) return current;
        return nextEnabled(target, step);
      });
    },
    [columns, itemCount, nextEnabled],
  );

  /** 각 셀에 펼쳐 넣는다. 그리드 전체가 탭 정지점 하나가 된다. */
  const getCellProps = useCallback(
    (index: number) => ({
      tabIndex: index === activeIndex ? 0 : -1,
      onFocus: () => setActiveIndex(index),
    }),
    [activeIndex],
  );

  return { activeIndex, onKeyDown, getCellProps };
}
