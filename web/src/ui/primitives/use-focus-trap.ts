"use client";

import { useEffect, useRef, type RefObject } from "react";

// 기존 nav-links.tsx의 모바일 드로어가 이미 포커스 트랩+Escape+포커스 복원 패턴을
// 구현해뒀다(§7.1). 이 훅은 그 접근 방식을 Dialog/Drawer가 공유할 수 있는 형태로
// 새로 구현한 것 — nav-links.tsx는 1단계 범위 밖이라 건드리지 않는다.
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function useFocusTrap({
  open,
  onClose,
  containerRef,
  restoreFocusRef,
}: {
  open: boolean;
  onClose: () => void;
  containerRef: RefObject<HTMLElement | null>;
  restoreFocusRef?: { current: HTMLElement | null };
}) {
  const previouslyFocused = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    previouslyFocused.current = document.activeElement as HTMLElement | null;

    const container = containerRef.current;
    const focusables = container ? Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)) : [];
    (focusables[0] ?? container)?.focus();
    // cleanup 시점에 ref가 이미 바뀌었을 수 있으므로(react-hooks/exhaustive-deps) 이펙트
    // 시작 시점 값을 스냅샷해둔다 — 트리거 요소는 다이얼로그가 열려 있는 동안 바뀌지 않는다.
    const restoreTarget = restoreFocusRef?.current;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key !== "Tab" || !container) return;
      const items = Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
      if (items.length === 0) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      (restoreTarget ?? previouslyFocused.current)?.focus();
    };
  }, [open, onClose, containerRef, restoreFocusRef]);
}
