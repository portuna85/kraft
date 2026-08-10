"use client";

import { useState } from "react";

import { reportContent } from "../api";
import {
  REPORT_REASONS,
  REPORT_REASON_LABELS,
  type ReportReason,
  type ReportTargetType,
} from "../schema";
import { Button } from "@/shared/ui/button";
import { Dialog } from "@/shared/ui/dialog";
import { Radio } from "@/shared/ui/field";
import { InlineAlert } from "@/shared/ui/states";

import styles from "./report-dialog.module.css";

/**
 * 신고 사유 선택 다이얼로그 본체 — `report-dialog.tsx`가 열릴 때만
 * `next/dynamic`으로 이 모듈을 불러온다(improvement_fe.md P-3). 트리거 버튼은
 * 항상 렌더돼야 하므로 여기 들어오지 않는다.
 */
export function ReportDialogBody({
  targetType,
  targetId,
  onClose,
  onDone,
}: {
  targetType: ReportTargetType;
  targetId: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const [reason, setReason] = useState<ReportReason>("SPAM");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    setPending(true);
    setError(null);
    try {
      await reportContent(targetType, targetId, reason);
      onDone();
    } catch {
      setError("신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open onClose={onClose} title="신고 사유 선택" size="sm">
      <div className={styles.reasons} role="radiogroup" aria-label="신고 사유">
        {REPORT_REASONS.map((value) => (
          <Radio
            key={value}
            name="report-reason"
            label={REPORT_REASON_LABELS[value]}
            value={value}
            checked={reason === value}
            onChange={() => setReason(value)}
          />
        ))}
      </div>

      {error !== null && <InlineAlert tone="danger">{error}</InlineAlert>}

      <div className={styles.dialogActions}>
        <Button variant="quiet" onClick={onClose} disabled={pending}>
          취소
        </Button>
        {pending ? (
          <Button variant="danger" loading loadingLabel="접수 중">
            신고하기
          </Button>
        ) : (
          <Button variant="danger" onClick={submit}>
            신고하기
          </Button>
        )}
      </div>
    </Dialog>
  );
}
