"use client";

import { useCallback, useRef, useState, type KeyboardEvent } from "react";

/**
 * 45칸 번호 격자처럼 "한 번의 Tab으로 진입하고 방향키로 이동하는" 그리드용 로빙 tabindex.
 *
 * FE-017: `/companion`은 이 동작을 갖고 있었지만 `/recommend`의 NumberBoard는 없어서,
 * 같은 45칸 격자인데 한쪽은 Tab 45번을 눌러야 생성 버튼에 도달할 수 있었다.
 * 두 화면이 같은 구현을 쓰도록 여기로 추출했다(FE-107의 중복 제거도 겸한다).
 *
 * 열 수는 브레이크포인트마다 다르므로(모바일 5·태블릿 7·데스크톱 9) 상수로 두지 않고
 * 실제 렌더된 첫 행의 버튼 개수를 offsetTop으로 측정한다 — CSS가 바뀌어도 따라간다.
 *
 * M-9: isItemDisabled 없이는 moveFocus가 순수 인덱스 산술만 해서, 비활성 셀에
 * tabIndex/focus를 얹으려다 desync가 났다(네이티브 disabled 버튼은 .focus()가 no-op이라
 * DOM 포커스는 이전 셀에 남는데 roving 마커만 옮겨감 — 이후 화살표 키가 낡은 요소에서
 * 발화하고, Tab으로 나갔다 돌아오면 그리드의 유일한 탭 스톱이 비활성 버튼이라 재진입 자체가
 * 소실됐다). 이제 활성 셀만 순회하고, 외부 상태 변화로 현재 tabIndex가 비활성이 되면
 * 가까운 활성 셀로 마커를 옮긴다.
 */
export function useRovingGrid(itemCount: number, isItemDisabled?: (index: number) => boolean) {
  const [focusedIndex, setFocusedIndex] = useState(0);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const isDisabled = useCallback(
    (index: number) => isItemDisabled?.(index) ?? false,
    [isItemDisabled]
  );

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

  /** start부터 step 방향으로 훑어 첫 활성 셀의 인덱스를 찾는다. 없으면 null. */
  function findEnabledIndex(start: number, step: 1 | -1): number | null {
    let index = start;
    while (index >= 0 && index < itemCount) {
      if (!isDisabled(index)) return index;
      index += step;
    }
    return null;
  }

  function moveFocus(candidate: number, step: 1 | -1) {
    const clamped = Math.max(0, Math.min(itemCount - 1, candidate));
    const target = findEnabledIndex(clamped, step);
    if (target === null) return;
    setFocusedIndex(target);
    itemRefs.current[target]?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    switch (event.key) {
      case "ArrowRight":
        event.preventDefault();
        moveFocus(focusedIndex + 1, 1);
        break;
      case "ArrowLeft":
        event.preventDefault();
        moveFocus(focusedIndex - 1, -1);
        break;
      case "ArrowDown":
        event.preventDefault();
        moveFocus(focusedIndex + getColumnCount(), 1);
        break;
      case "ArrowUp":
        event.preventDefault();
        moveFocus(focusedIndex - getColumnCount(), -1);
        break;
      case "Home":
        event.preventDefault();
        moveFocus(0, 1);
        break;
      case "End":
        event.preventDefault();
        moveFocus(itemCount - 1, -1);
        break;
      default:
        break;
    }
  }

  // 사용자 조작(방향키) 없이도 외부 상태가 바뀌어 현재 탭 스톱이 비활성화될 수 있다
  // (예: 6개 선택 완료로 나머지 칸이 한꺼번에 비활성화). 그대로 두면 그리드의 유일한
  // 탭 스톱이 비활성 버튼이 돼 재진입 자체가 사라진다 — effect로 상태를 되돌리는 대신
  // (커밋마다 렌더가 하나 더 생긴다) 렌더 중에 "실제 유효한" 탭 스톱을 그때그때
  // 계산한다. focusedIndex 자체는 바꾸지 않는다: 다음 방향키 이동은 여전히 사용자가
  // 마지막으로 있던 위치를 기준으로 계산돼야 자연스럽다.
  const effectiveFocusedIndex = isDisabled(focusedIndex)
    ? (findEnabledIndex(focusedIndex, 1) ?? findEnabledIndex(focusedIndex, -1) ?? focusedIndex)
    : focusedIndex;

  /** 각 항목에 펼쳐 넣는다 — tabIndex는 포커스된 하나만 0이 된다. */
  function getItemProps(index: number) {
    return {
      ref: (el: HTMLButtonElement | null) => {
        itemRefs.current[index] = el;
      },
      tabIndex: effectiveFocusedIndex === index ? 0 : -1,
      onFocus: () => setFocusedIndex(index),
    };
  }

  return { focusedIndex, setFocusedIndex, handleKeyDown, getItemProps };
}
