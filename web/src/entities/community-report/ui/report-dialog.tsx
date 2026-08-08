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
 * 신고 — improvement_fe.md §23.10, §3.6
 *
 * 사유 7종은 백엔드 열거값이라 늘리거나 줄이지 않는다. 라디오로 두는 이유는 하나만
 * 고를 수 있다는 것을 형태로 알리기 위해서다.
 *
 * 접수 결과를 다이얼로그 **안에서** 보여준 뒤 닫는다. 밖으로 내보내면 다이얼로그가
 * 닫힌 뒤 어디에도 안 보이는 결과가 생긴다(ConfirmDialog와 같은 이유).
 */
export function ReportDialog({
  targetType,
  targetId,
  label,
}: {
  targetType: ReportTargetType;
  targetId: number;
  label: string;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>("SPAM");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function submit() {
    setPending(true);
    setError(null);
    try {
      await reportContent(targetType, targetId, reason);
      setDone(true);
      setOpen(false);
    } catch {
      setError("신고를 접수하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setPending(false);
    }
  }

  if (done) {
    return (
      <p className={styles.note} role="status">
        신고가 접수됐습니다. 운영진이 확인합니다.
      </p>
    );
  }

  return (
    <>
      <Button variant="quiet" onClick={() => setOpen(true)}>
        {label}
      </Button>

      <Dialog open={open} onClose={() => setOpen(false)} title="신고 사유 선택" size="sm">
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
          <Button variant="quiet" onClick={() => setOpen(false)} disabled={pending}>
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
    </>
  );
}
