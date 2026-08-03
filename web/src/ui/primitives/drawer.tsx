"use client";

import { useRef } from "react";
import { createPortal } from "react-dom";
import type { DrawerContract } from "./contracts";
import { IconButton } from "./icon-button";
import { useFocusTrap, getOverlayHost } from "./use-focus-trap";
import styles from "./drawer.module.css";

const SIDE_CLASS = {
  left: styles.left,
  right: styles.right,
  bottom: styles.bottom,
} as const;

export function Drawer({
  open,
  onClose,
  restoreFocusRef,
  side,
  titleId,
  title,
  closeLabel = "닫기",
  children,
}: DrawerContract) {
  const panelRef = useRef<HTMLDivElement>(null);
  useFocusTrap({ open, onClose, containerRef: panelRef, restoreFocusRef });

  if (!open) return null;

  // H-1: Dialog와 동일하게 body 레벨 호스트로 포털한다.
  return createPortal(
    <div className={styles.backdrop} data-drawer-backdrop onClick={onClose}>
      <div
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`${styles.panel} ${SIDE_CLASS[side]}`}
        data-drawer-panel
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.header}>
          <h2 id={titleId} className={styles.title}>
            {title}
          </h2>
          <IconButton
            aria-label={closeLabel}
            variant="quiet"
            icon={<span aria-hidden="true">×</span>}
            onClick={onClose}
          />
        </div>
        {children}
      </div>
    </div>,
    getOverlayHost()
  );
}
