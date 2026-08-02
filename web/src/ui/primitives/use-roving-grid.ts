"use client";

import { useRef, useState, type KeyboardEvent } from "react";

/**
 * 45칸 번호 격자처럼 "한 번의 Tab으로 진입하고 방향키로 이동하는" 그리드용 로빙 tabindex.
 *
 * FE-017: `/companion`은 이 동작을 갖고 있었지만 `/recommend`의 NumberBoard는 없어서,
 * 같은 45칸 격자인데 한쪽은 Tab 45번을 눌러야 생성 버튼에 도달할 수 있었다.
 * 두 화면이 같은 구현을 쓰도록 여기로 추출했다(FE-107의 중복 제거도 겸한다).
 *
 * 열 수는 브레이크포인트마다 다르므로(모바일 5·태블릿 7·데스크톱 9) 상수로 두지 않고
 * 실제 렌더된 첫 행의 버튼 개수를 offsetTop으로 측정한다 — CSS가 바뀌어도 따라간다.
 */
export function useRovingGrid(itemCount: number) {
  const [focusedIndex, setFocusedIndex] = useState(0);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  function getColumnCount(): number {
    const first = itemRefs.current[0];
    if (!first) return 1;
    const firstTop = first.offsetTop;
    let count = 0;
    for (const el of itemRefs.current) {
      if (!el || el.offsetTop !== firstTop) break;
      count += 1;
    }
    return count || 1;
  }

  function moveFocus(nextIndex: number) {
    const clamped = Math.max(0, Math.min(itemCount - 1, nextIndex));
    setFocusedIndex(clamped);
    itemRefs.current[clamped]?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    switch (event.key) {
      case "ArrowRight":
        event.preventDefault();
        moveFocus(focusedIndex + 1);
        break;
      case "ArrowLeft":
        event.preventDefault();
        moveFocus(focusedIndex - 1);
        break;
      case "ArrowDown":
        event.preventDefault();
        moveFocus(focusedIndex + getColumnCount());
        break;
      case "ArrowUp":
        event.preventDefault();
        moveFocus(focusedIndex - getColumnCount());
        break;
      case "Home":
        event.preventDefault();
        moveFocus(0);
        break;
      case "End":
        event.preventDefault();
        moveFocus(itemCount - 1);
        break;
      default:
        break;
    }
  }

  /** 각 항목에 펼쳐 넣는다 — tabIndex는 포커스된 하나만 0이 된다. */
  function getItemProps(index: number) {
    return {
      ref: (el: HTMLButtonElement | null) => {
        itemRefs.current[index] = el;
      },
      tabIndex: focusedIndex === index ? 0 : -1,
      onFocus: () => setFocusedIndex(index),
    };
  }

  return { focusedIndex, setFocusedIndex, handleKeyDown, getItemProps };
}
