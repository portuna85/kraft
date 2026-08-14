"use client";

import { useEffect, useState } from "react";

/**
 * 가상 키보드 열림 감지
 *
 * 가상 키보드가 열리면 모바일 브라우저의 visualViewport.height가 레이아웃
 * 뷰포트보다 크게 줄어든다(키보드가 차지한 만큼) — 마운트 시점의 높이를 기준값으로 잡고,
 * 폭이 그대로인 채 높이만 그 기준의 일정 비율 이상 줄면 키보드가 열린 것으로 간주한다.
 * 폭이 바뀌면 회전/리사이즈로 보고 기준값을 다시 잡는다(오탐 방지).
 */
const SHRINK_RATIO = 0.75;

export function useKeyboardOpen(): boolean {
  const [keyboardOpen, setKeyboardOpen] = useState(false);

  useEffect(() => {
    const viewport = window.visualViewport;
    if (!viewport) return;

    let baselineWidth = viewport.width;
    let baselineHeight = viewport.height;

    function handleResize() {
      if (!viewport) return;
      if (viewport.width !== baselineWidth) {
        baselineWidth = viewport.width;
        baselineHeight = viewport.height;
        setKeyboardOpen(false);
        return;
      }
      setKeyboardOpen(viewport.height < baselineHeight * SHRINK_RATIO);
    }

    viewport.addEventListener("resize", handleResize);
    return () => viewport.removeEventListener("resize", handleResize);
  }, []);

  return keyboardOpen;
}
