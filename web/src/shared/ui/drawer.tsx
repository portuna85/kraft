"use client";

import { useEffect, useId, type MouseEvent, type ReactNode } from "react";

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
