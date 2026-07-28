"use client";

import { useRef, useState } from "react";
import { Dialog } from "@/ui/primitives/dialog";
import { reportContent } from "@/lib/community-client";
import { REPORT_REASON_LABELS } from "./types";

export function ReportDialog({
  targetType,
  targetId,
  triggerLabel = "신고",
}: {
  targetType: "POST" | "COMMENT" | "USER";
  targetId: number;
  triggerLabel?: string;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("SPAM");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);

  async function submit() {
    setSubmitting(true);
    setMessage("");
    try {
      await reportContent(targetType, targetId, reason);
      setMessage("신고가 접수되었습니다.");
      setOpen(false);
    } catch {
      setMessage("이미 신고했거나 처리하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <button type="button" ref={triggerRef} onClick={() => setOpen(true)}>
        {triggerLabel}
      </button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        restoreFocusRef={triggerRef}
        titleId="report-dialog-title"
        title="신고하기"
      >
        <label htmlFor="report-reason">신고 사유</label>
        <select id="report-reason" value={reason} onChange={(event) => setReason(event.target.value)}>
          {Object.entries(REPORT_REASON_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <div>
          <button type="button" onClick={() => setOpen(false)}>
            취소
          </button>
          <button type="button" onClick={submit} disabled={submitting}>
            {submitting ? "제출 중..." : "신고 제출"}
          </button>
        </div>
        {message ? <p role="status">{message}</p> : null}
      </Dialog>
    </>
  );
}
