"use client";

import {
  useEffect,
  useId,
  type MouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from "react";

import { useEventCallback } from "@/shared/hooks/use-event-callback";
import { useFocusTrap } from "@/shared/hooks/use-focus-trap";

import { IconButton } from "./button";
import styles from "./overlay.module.css";

/**
 * Drawer
 *
 * Dialog와 같은 포커스 규칙을 따르되 **가시적 닫기 버튼이 필수**다. 모바일에서 드로어를
 * 배경 탭으로만 닫게 하면 배경이 안 보이는 전체 화면 드로어에서 빠져나갈 방법이 사라진다.
 */
/** 아래로 이만큼 끌면 닫는다(§04 2.2). 짧으면 스크롤과 헷갈린다. */
const SWIPE_CLOSE_THRESHOLD_PX = 80;

export function Drawer({
  open,
  onClose,
  title,
  side = "right",
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  side?: "left" | "right" | "bottom";
  children: ReactNode;
}) {
  const titleId = useId();
  const panelRef = useFocusTrap<HTMLDivElement>(open);
  // TD-013: dialog.tsx와 동일한 이유 — open이 유지되는 동안 새 onClose가 와도
  // Escape가 오래된 콜백을 호출하지 않게 안정적인 래퍼를 쓴다.
  const stableOnClose = useEventCallback(onClose);

  useEffect(() => {
    if (!open) return;

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") stableOnClose();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, stableOnClose]);

  if (!open) return null;

  const sideClass =
    side === "left"
      ? styles.drawerLeft
      : side === "bottom"
        ? styles.drawerBottom
        : styles.drawerRight;

  function onBackdropClick(event: MouseEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget) onClose();
  }

  /**
   * 아래로 스와이프해 닫기. 끄는 동안 패널을 따라 움직이게 하려면 프레임마다 인라인
   * transform이 필요한데, 그러면 style-src에서 'unsafe-inline'을 뺄 수 없다(§20.3).
   * 그래서 제스처는 판정만 하고(80px 이상 아래로) 시각 추종은 하지 않는다.
   * 닫기 버튼은 그대로 있으므로 이 제스처가 유일한 탈출구인 적은 없다.
   */
  function onHandlePointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    const startY = event.clientY;
    const handle = event.currentTarget;
    handle.setPointerCapture(event.pointerId);

    function onPointerUp(upEvent: PointerEvent) {
      handle.removeEventListener("pointerup", onPointerUp);
      handle.removeEventListener("pointercancel", onPointerUp);
      if (upEvent.clientY - startY >= SWIPE_CLOSE_THRESHOLD_PX) stableOnClose();
    }

    handle.addEventListener("pointerup", onPointerUp);
    handle.addEventListener("pointercancel", onPointerUp);
  }

  return (
    <div className={`${styles.drawerBackdrop} ${sideClass}`} onClick={onBackdropClick}>
      <div
        ref={panelRef}
        className={styles.drawerPanel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        {side === "bottom" && (
          // 장식이자 제스처 손잡이다 — 닫기 자체는 헤더의 닫기 버튼이 담당한다.
          <div
            className={styles.drawerHandle}
            aria-hidden="true"
            onPointerDown={onHandlePointerDown}
          />
        )}

        <div className={styles.drawerHeader}>
          <h2 id={titleId}>{title}</h2>
          <IconButton
            aria-label="닫기"
            icon={<span aria-hidden="true">✕</span>}
            onClick={onClose}
          />
        </div>
        {children}
      </div>
    </div>
  );
}
