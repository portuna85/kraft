"use client";

import { useEffect, useRef, type RefObject } from "react";

// 기존 nav-links.tsx의 모바일 드로어가 이미 포커스 트랩+Escape+포커스 복원 패턴을
// 구현해뒀다. 이 훅은 그 접근 방식을 Dialog/Drawer가 공유할 수 있는 형태로
// 새로 구현한 것 — nav-links.tsx는 1단계 범위 밖이라 건드리지 않는다.
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

const OVERLAY_HOST_ID = "kraft-overlay-root";

/**
 * H-1: Dialog/Drawer가 포털할 단일 body 레벨 호스트. 모든 오버레이가 이 하나의 노드에
 * 렌더되므로, 배경 격리는 "이 호스트를 제외한 body의 모든 직계 자식"만 처리하면 된다 —
 * 예전처럼 패널의 실제 React 트리 위치를 따라 조상마다 형제를 훑을 필요가 없다(그 방식은
 * body 레벨(Header/main/Footer/MobileBottomNav/StickyMobileAd)에 도달하기 전에 멈췄다).
 */
export function getOverlayHost(): HTMLElement {
  let host = document.getElementById(OVERLAY_HOST_ID);
  if (!host) {
    host = document.createElement("div");
    host.id = OVERLAY_HOST_ID;
    document.body.appendChild(host);
  }
  return host;
}

type InertedElement = { element: HTMLElement; ariaHidden: string | null; inert: string | null };

function hideBodyChildrenExceptHost(host: HTMLElement): InertedElement[] {
  const hidden: InertedElement[] = [];
  for (const child of Array.from(document.body.children)) {
    if (child === host || !(child instanceof HTMLElement)) continue;
    hidden.push({
      element: child,
      ariaHidden: child.getAttribute("aria-hidden"),
      inert: child.getAttribute("inert"),
    });
    child.setAttribute("aria-hidden", "true");
    child.setAttribute("inert", "");
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

// H-1: 오버레이가 중첩 열릴 수 있다(예: 확인 다이얼로그가 다른 다이얼로그/드로어 위에
// 뜨는 경우). 문서(탭)당 오버레이 시스템은 하나뿐이므로 모듈 전역 참조 카운트로 관리한다
// — 안쪽 오버레이가 먼저 닫혀도 바깥쪽이 열려 있는 동안은 배경을 복원하지 않는다.
let openOverlayCount = 0;
let savedBackgroundState: InertedElement[] | null = null;

function acquireBackgroundHide(host: HTMLElement) {
  openOverlayCount += 1;
  if (openOverlayCount === 1) {
    savedBackgroundState = hideBodyChildrenExceptHost(host);
  }
}

function releaseBackgroundHide() {
  openOverlayCount = Math.max(0, openOverlayCount - 1);
  if (openOverlayCount === 0 && savedBackgroundState) {
    restoreBackground(savedBackgroundState);
    savedBackgroundState = null;
  }
}

export function useFocusTrap({
  open,
  onClose,
  containerRef,
  restoreFocusRef,
  initialFocusRef,
}: {
  open: boolean;
  onClose: () => void;
  containerRef: RefObject<HTMLElement | null>;
  restoreFocusRef?: { current: HTMLElement | null };
  initialFocusRef?: { current: HTMLElement | null };
}) {
  const previouslyFocused = useRef<HTMLElement | null>(null);

  // FE-099: 이 이펙트의 의존성에 onClose가 있으면, 호출부가 인라인 화살표 함수를 넘길 때
  // (MobileSecondaryMenu가 그랬다) 부모가 리렌더될 때마다 cleanup→재설정이 돌아
  // 배경 inert 복원, body overflow 복원, 포커스 강제 이동이 매번 다시 일어난다.
  // 열린 다이얼로그 안에서 입력할 때 포커스가 튀는 원인이므로, 최신 콜백을 ref로만
  // 읽고 의존성에서 뺀다. containerRef/restoreFocusRef도 ref 객체라 재구독이 불필요하다.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return;
    previouslyFocused.current = document.activeElement as HTMLElement | null;

    const container = containerRef.current;
    const focusables = container ? Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)) : [];
    (initialFocusRef?.current ?? focusables[0] ?? container)?.focus();

    const host = getOverlayHost();
    acquireBackgroundHide(host);

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
        onCloseRef.current();
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
      releaseBackgroundHide();
      (restoreTarget ?? previouslyFocused.current)?.focus();
    };
    // onClose는 onCloseRef로 읽고, containerRef/restoreFocusRef/initialFocusRef는
    // 안정적인 ref 객체다. 이 목록에 다시 넣으면 FE-099(리렌더마다 트랩 재설정)가 재발한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);
}
