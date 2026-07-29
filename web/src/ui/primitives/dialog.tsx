"use client";

import { useRef } from "react";
import type { DialogContract } from "./contracts";
import { useFocusTrap } from "./use-focus-trap";
import styles from "./dialog.module.css";

export function Dialog({ open, onClose, restoreFocusRef, titleId, title, children }: DialogContract) {
  const panelRef = useRef<HTMLDivElement>(null);
  useFocusTrap({ open, onClose, containerRef: panelRef, restoreFocusRef });

  if (!open) return null;

  return (
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
        <h2 id={titleId} className={styles.title}>
          {title}
        </h2>
        {children}
      </div>
    </div>
  );
}
