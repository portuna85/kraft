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

  const [submitted, setSubmitted] = useState(false);

  // 한 화면에 신고 다이얼로그가 여러 개(게시글 1 + 댓글 N) 놓이므로 title id를 대상별로 고유하게 만든다.
  const titleId = `report-dialog-title-${targetType.toLowerCase()}-${targetId}`;

  async function submit() {
    setSubmitting(true);
    setMessage("");
    try {
      await reportContent(targetType, targetId, reason);
      setMessage("신고가 접수되었습니다.");
      setSubmitted(true);
      // F-P0-8: 이전에는 여기서 곧바로 setOpen(false)를 호출했다 — Dialog는 !open이면
      // null을 반환하므로 메시지가 화면에 뜨는 순간이 아예 없었다. 성공해도 다이얼로그는
      // 열어둔 채 메시지를 보여주고, 사용자가 "확인"을 눌러야 닫히도록 바꾼다.
    } catch {
      setMessage("이미 신고했거나 처리하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  function close() {
    setOpen(false);
    setMessage("");
    setSubmitted(false);
  }

  return (
    <>
      <button type="button" ref={triggerRef} onClick={() => setOpen(true)}>
        {triggerLabel}
      </button>
      <Dialog
        open={open}
        onClose={close}
        restoreFocusRef={triggerRef}
        titleId={titleId}
        title="신고하기"
      >
        {submitted ? (
          <>
            <p role="status">{message}</p>
            <div>
              <button type="button" onClick={close}>
                확인
              </button>
            </div>
          </>
        ) : (
          <>
            <label htmlFor="report-reason">신고 사유</label>
            <select id="report-reason" value={reason} onChange={(event) => setReason(event.target.value)}>
              {Object.entries(REPORT_REASON_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
            <div>
              <button type="button" onClick={close}>
                취소
              </button>
              <button type="button" onClick={submit} disabled={submitting}>
                {submitting ? "제출 중..." : "신고 제출"}
              </button>
            </div>
            {message ? <p role="status">{message}</p> : null}
          </>
        )}
      </Dialog>
    </>
  );
}
