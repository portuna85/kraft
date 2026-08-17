"use client";

import { useEffect } from "react";

/**
 * 배경 스크롤 잠금
 *
 * I-26: Dialog에 처음 있던 문제 — 열려 있는 동안 배경 스크롤이 잠기지 않아, 백드롭
 * 위에서 휠을 굴리면 뒷 페이지가 스크롤됐다. Drawer는 포커스 트랩·Escape는 있었지만
 * 이 잠금이 아예 없었다 — 오버레이마다 따로 구현하면 하나씩 빠뜨리기 쉬우므로 공유
 * 훅으로 뽑는다.
 *
 * iOS Safari의 탄성(rubber-band) 오버스크롤은 `overflow: hidden`만으로는 완전히
 * 막히지 않는 알려진 한계다 — `position: fixed` 기반 우회는 이번 범위에 넣지 않는다.
 */
export function useScrollLock(active: boolean) {
  useEffect(() => {
    if (!active) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [active]);
}
