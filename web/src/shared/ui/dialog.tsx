"use client";

import { useEffect, useId, type MouseEvent } from "react";

import { useEventCallback } from "@/shared/hooks/use-event-callback";
import { useFocusTrap } from "@/shared/hooks/use-focus-trap";
import { useScrollLock } from "@/shared/hooks/use-scroll-lock";

import { Button, IconButton } from "./button";
import type { ConfirmDialogContract, DialogContract } from "./contracts";
import styles from "./dialog.module.css";
import { InlineAlert } from "./states";

/**
 * Dialog
 *
 * `open`이 false면 아무것도 렌더하지 않는다. 숨김 처리로 DOM에 남겨두면 포커스 트랩과
 * 실제 표시 상태가 어긋나 닫힌 다이얼로그 안으로 Tab이 들어간다(레거시 F-P0-8).
 *
 * 포커스 관리는 useFocusTrap이 전담한다 — 열 때 진입, Tab 순환, 닫을 때 원위치 복귀.
 */
export function Dialog({ open, onClose, title, size = "md", children }: DialogContract) {
  const titleId = useId();
  const panelRef = useFocusTrap<HTMLDivElement>(open);
  // TD-013: open이 true로 유지되는 동안 호출부가 새 onClose 클로저를 넘겨도
  // Escape가 오래된 콜백을 호출하지 않도록 안정적인 래퍼를 통해서만 부른다.
  const stableOnClose = useEventCallback(onClose);

  useEffect(() => {
    if (!open) return;

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") stableOnClose();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, stableOnClose]);

  // I-26 원인: use-scroll-lock.ts 참고 — Drawer와 공유하는 프리미티브라 여기서 한 번만
  // 고치면 ConfirmDialog(삭제·탈퇴 확인)를 포함한 모든 소비자에 적용된다.
  useScrollLock(open);

  if (!open) return null;

  function onBackdropClick(event: MouseEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget) onClose();
  }

  return (
    <div className={styles.backdrop} onClick={onBackdropClick}>
      <div
        ref={panelRef}
        className={`${styles.panel} ${styles[size]}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <div className={styles.header}>
          <h2 id={titleId} className={styles.title}>
            {title}
          </h2>
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

/**
 * ConfirmDialog — 파괴적 액션의 단일 확인 경로(레거시 FE-003).
 *
 * 과거에는 window.confirm과 5초 undo와 확인 없음이 화면마다 섞여 있었다. 하나로 모으는
 * 것이 목적이므로 `window.confirm` 사용은 수용 기준에서 0건이어야 한다(§29.3).
 *
 * `pending` 중에는 **양쪽 버튼을 모두 잠근다**. 취소만 열어두면 요청이 날아가는 중에
 * 다이얼로그가 닫혀 결과를 표시할 곳이 사라진다. 오류도 다이얼로그 **안에서** 보여준다.
 */
export function ConfirmDialog({
  open,
  onClose,
  title,
  variant = "default",
  description,
  confirmLabel,
  cancelLabel = "취소",
  onConfirm,
  pending = false,
  errorMessage = null,
}: ConfirmDialogContract) {
  return (
    <Dialog open={open} onClose={onClose} title={title} size="sm">
      <div className={styles.description}>{description}</div>

      {errorMessage !== null && <InlineAlert tone="danger">{errorMessage}</InlineAlert>}

      <div className={styles.actions}>
        <Button variant="secondary" onClick={onClose} disabled={pending}>
          {cancelLabel}
        </Button>
        {pending ? (
          <Button
            variant={variant === "danger" ? "danger" : "primary"}
            loading
            loadingLabel="처리 중"
          >
            {confirmLabel}
          </Button>
        ) : (
          <Button variant={variant === "danger" ? "danger" : "primary"} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        )}
      </div>
    </Dialog>
  );
}
