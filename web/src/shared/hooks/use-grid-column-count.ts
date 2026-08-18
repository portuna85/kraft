"use client";

import { useEffect, useRef, useState, type RefObject } from "react";

/**
 * I-14: `repeat(auto-fill, minmax(...))` 그리드의 실제 렌더 열 수 추적.
 *
 * 열 수가 CSS 런타임 계산에 달려 있으면(뷰포트·컨테이너 폭에 따라 달라짐) 키보드
 * 위/아래 이동 폭을 하드코딩한 상수로 계산할 수 없다 — 실제 줄바꿈 위치와 어긋나
 * 화살표가 같은 줄 안에서만 움직이거나 엉뚱한 셀로 건너뛴다. `use-element-width.ts`와
 * 같은 ResizeObserver 패턴을 쓰되, 폭이 아니라 `getComputedStyle(...).gridTemplateColumns`의
 * 트랙 개수를 읽는다.
 */
export function useGridColumnCount<T extends HTMLElement>(
  fallback: number,
): [RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [columns, setColumns] = useState(fallback);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    function measure() {
      const current = ref.current;
      if (!current) return;
      const value = getComputedStyle(current).gridTemplateColumns;
      // used value가 아니라 지정값(`repeat(auto-fill, minmax(44px, 1fr))`)이 그대로
      // 돌아오면(레이아웃 전이거나 display:none) 공백 split이 실제 열 수와 무관해진다.
      // minmax()처럼 공백을 포함한 함수 토큰도 과다 계산을 만드므로, px 트랙 개수만
      // 정규식으로 센다 — repeat(가 남아 있으면 계산하지 않고 fallback을 유지한다.
      if (value.includes("repeat(")) return;
      const count = value.match(/\d+(\.\d+)?px/g)?.length ?? 0;
      if (count > 0) setColumns(count);
    }

    measure();

    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return [ref, columns];
}
