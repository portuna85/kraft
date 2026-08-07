"use client";

import { useEffect, useRef } from "react";

const FOCUSABLE = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  '[tabindex]:not([tabindex="-1"])',
].join(",");

function focusableWithin(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => element.offsetParent !== null || element === document.activeElement,
  );
}

/**
 * 오버레이 포커스 트랩 — improvement_fe.md §12.3
 *
 * 세 가지를 함께 처리한다. 하나라도 빠지면 키보드 사용자가 갇히거나 길을 잃는다:
 *   1. 열릴 때 오버레이 안으로 포커스를 옮긴다
 *   2. Tab이 오버레이 밖으로 나가지 않는다
 *   3. 닫힐 때 **열기 전 요소로 포커스를 되돌린다**
 *
 * 3번을 빼먹기 쉬운데, 없으면 다이얼로그를 닫은 순간 포커스가 body로 떨어져
 * 키보드 사용자는 처음부터 Tab을 다시 눌러야 한다.
 */
export function useFocusTrap<T extends HTMLElement>(active: boolean) {
  const containerRef = useRef<T | null>(null);
  const restoreToRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!active) return;

    const container = containerRef.current;
    if (!container) return;

    restoreToRef.current = document.activeElement as HTMLElement | null;
    (focusableWithin(container)[0] ?? container).focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== "Tab" || !container) return;

      const focusable = focusableWithin(container);
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) {
        event.preventDefault();
        return;
      }

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      restoreToRef.current?.focus();
    };
  }, [active]);

  return containerRef;
}
