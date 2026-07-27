"use client";

import { useEffect } from "react";
import type { ToastContract, ToastTone } from "./contracts";
import { IconButton } from "./icon-button";
import styles from "./toast.module.css";

const TONE_CLASS: Record<ToastTone, string> = {
  neutral: styles.neutral,
  success: styles.success,
  danger: styles.danger,
};

export function Toast({ tone, message, politeness, durationMs, onDismiss }: ToastContract) {
  useEffect(() => {
    if (!durationMs) return;
    const timer = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(timer);
  }, [durationMs, onDismiss]);

  return (
    <div className={`${styles.toast} ${TONE_CLASS[tone]}`} role="status" aria-live={politeness}>
      <span className={styles.message}>{message}</span>
      <IconButton aria-label="닫기" variant="quiet" size="sm" icon={<span>×</span>} onClick={onDismiss} />
    </div>
  );
}
