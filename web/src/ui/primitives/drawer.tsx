"use client";

import { useRef } from "react";
import type { DrawerContract } from "./contracts";
import { useFocusTrap } from "./use-focus-trap";
import styles from "./drawer.module.css";

const SIDE_CLASS = {
  left: styles.left,
  right: styles.right,
  bottom: styles.bottom,
} as const;

export function Drawer({ open, onClose, restoreFocusRef, side, titleId, title, children }: DrawerContract) {
  const panelRef = useRef<HTMLDivElement>(null);
  useFocusTrap({ open, onClose, containerRef: panelRef, restoreFocusRef });

  if (!open) return null;

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`${styles.panel} ${SIDE_CLASS[side]}`}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id={titleId} className={styles.title}>
          {title}
        </h2>
        {children}
      </div>
    </div>
  );
}
