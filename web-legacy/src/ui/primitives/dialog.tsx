"use client";

import { useRef } from "react";
import { createPortal } from "react-dom";
import type { DialogContract } from "./contracts";
import { IconButton } from "./icon-button";
import { useFocusTrap, getOverlayHost } from "./use-focus-trap";
import styles from "./dialog.module.css";

export function Dialog({
  open,
  onClose,
  restoreFocusRef,
  initialFocusRef,
  titleId,
  title,
  children,
}: DialogContract) {
  const panelRef = useRef<HTMLDivElement>(null);
  useFocusTrap({ open, onClose, containerRef: panelRef, restoreFocusRef, initialFocusRef });

  if (!open) return null;

  // H-1: body 레벨 호스트로 포털한다 — 인라인 렌더링 위치에 따라 배경 격리가 일부만
  // 적용되던 문제(모달이 <main> 안에 있으면 <header>/<footer> 등은 격리되지 않음)를
  // "이 호스트를 제외한 body의 모든 자식을 격리"라는 단일 규칙으로 대체한다.
  return createPortal(
    <div className={styles.backdrop} onClick={onClose}>
      <div
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={styles.panel}
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.header}>
          <h2 id={titleId} className={styles.title}>
            {title}
          </h2>
          <IconButton aria-label="닫기" variant="quiet" size="sm" icon="×" onClick={onClose} />
        </div>
        {children}
      </div>
    </div>,
    getOverlayHost()
  );
}
