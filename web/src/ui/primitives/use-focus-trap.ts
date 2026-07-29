"use client";

import { useEffect, useRef, type RefObject } from "react";

// 기존 nav-links.tsx의 모바일 드로어가 이미 포커스 트랩+Escape+포커스 복원 패턴을
// 구현해뒀다. 이 훅은 그 접근 방식을 Dialog/Drawer가 공유할 수 있는 형태로
// 새로 구현한 것 — nav-links.tsx는 1단계 범위 밖이라 건드리지 않는다.
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

type InertedElement = { element: HTMLElement; ariaHidden: string | null; inert: string | null };

function hideBackground(container: HTMLElement): InertedElement[] {
  const hidden: InertedElement[] = [];
  let current: HTMLElement | null = container;
  while (current?.parentElement && current.parentElement !== document.body) {
    const parent: HTMLElement = current.parentElement;
    for (const sibling of Array.from(parent.children)) {
      if (sibling === current || !(sibling instanceof HTMLElement)) continue;
      hidden.push({
        element: sibling,
        ariaHidden: sibling.getAttribute("aria-hidden"),
        inert: sibling.getAttribute("inert"),
      });
      sibling.setAttribute("aria-hidden", "true");
      sibling.setAttribute("inert", "");
    }
    current = parent;
  }
  return hidden;
}

function restoreBackground(hidden: InertedElement[]) {
  for (const { element, ariaHidden, inert } of hidden) {
    if (ariaHidden === null) element.removeAttribute("aria-hidden");
    else element.setAttribute("aria-hidden", ariaHidden);
    if (inert === null) element.removeAttribute("inert");
    else element.setAttribute("inert", inert);
  }
}

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
    const hiddenBackground = container ? hideBackground(container) : [];
    // 모바일에서는 backdrop 뒤의 문서가 함께 스크롤되면 포커스 위치와 시각 위치가 어긋난다.
    // 기존 inline 값을 복원해 다른 레이아웃 정책을 덮어쓰지 않는다.
    const previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
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
      document.body.style.overflow = previousBodyOverflow;
      restoreBackground(hiddenBackground);
      (restoreTarget ?? previouslyFocused.current)?.focus();
    };
  }, [open, onClose, containerRef, restoreFocusRef]);
}
