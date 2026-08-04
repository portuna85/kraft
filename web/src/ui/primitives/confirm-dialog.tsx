"use client";

import { useId, useRef } from "react";
import type { ConfirmDialogContract } from "./contracts";
import { Dialog } from "./dialog";
import styles from "./confirm-dialog.module.css";

/**
 * 되돌릴 수 없는 작업의 공통 확인 절차(FE-003).
 *
 * `window.confirm`을 대신한다 — 네이티브 대화상자는 문구를 다듬을 수 없고, 스타일·포커스
 * 처리가 앱과 따로 놀며, 진행 중 상태나 실패 사유를 보여줄 자리가 없다.
 * 여기서는 Dialog primitive의 포커스 트랩·Escape·배경 격리를 그대로 쓰면서
 * 진행 중 잠금과 실패 표시까지 한곳에서 처리한다.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel = "취소",
  pending = false,
  errorMessage,
  onConfirm,
  onCancel,
}: ConfirmDialogContract) {
  const titleId = useId();
  const cancelRef = useRef<HTMLButtonElement>(null);

  return (
    <Dialog
      open={open}
      // 진행 중에는 Escape·배경 클릭으로도 닫히지 않게 한다 — 요청이 나간 뒤 닫으면
      // 결과를 어디에도 보여줄 수 없다.
      onClose={pending ? () => {} : onCancel}
      // L-9: 지정하지 않으면 포커스 트랩이 Dialog 헤더의 닫기 버튼(패널의 첫 포커스
      // 가능 요소)으로 가버려, 파괴적 확인의 기본 포커스가 "취소"가 아니게 된다.
      initialFocusRef={cancelRef}
      titleId={titleId}
      title={title}
    >
      {description ? <p className={styles.description}>{description}</p> : null}
      {errorMessage ? (
        <p className={styles.error} role="alert">
          {errorMessage}
        </p>
      ) : null}
      <div className={styles.actions}>
        <button
          type="button"
          ref={cancelRef}
          className="button secondary"
          onClick={onCancel}
          disabled={pending}
        >
          {cancelLabel}
        </button>
        <button type="button" className="button" onClick={onConfirm} disabled={pending}>
          {pending ? "처리 중…" : confirmLabel}
        </button>
      </div>
    </Dialog>
  );
}
