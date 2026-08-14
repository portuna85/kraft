"use client";

import dynamic from "next/dynamic";
import { useState } from "react";

import type { ReportTargetType } from "../schema";
import { Button } from "@/shared/ui/button";

import styles from "./report-dialog.module.css";

const ReportDialogBody = dynamic(() =>
  import("./report-dialog-body").then((module) => module.ReportDialogBody),
);

/**
 * 신고(P-3)
 *
 * 사유 7종은 백엔드 열거값이라 늘리거나 줄이지 않는다. 라디오로 두는 이유는 하나만
 * 고를 수 있다는 것을 형태로 알리기 위해서다.
 *
 * 신고 사유·API·스키마를 담은 본체(`report-dialog-body.tsx`)는 게시글 상세·댓글
 * 목록마다 항상 렌더되는 트리거 버튼과 달리 실제로 눌렀을 때만 필요하므로
 * `next/dynamic`으로 분리한다 — 트리거는 즉시 보이고, 본체 청크는 열 때만 받는다.
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
  const [done, setDone] = useState(false);

  if (done) {
    return (
      <p className={styles.note} role="status">
        신고가 접수됐습니다. 운영진이 확인합니다.
      </p>
    );
  }

  return (
    <>
      {/* danger ghost/text. 신고는 부차 행동이라
          full-width gradient primary처럼 강한 톤을 쓰면 안 되지만, quiet(중립
          색)로는 "위험/신고" 신호가 아예 없어져 danger 색의 ghost로 둔다. */}
      <Button variant="dangerQuiet" onClick={() => setOpen(true)}>
        {label}
      </Button>

      {open && (
        <ReportDialogBody
          targetType={targetType}
          targetId={targetId}
          onClose={() => setOpen(false)}
          onDone={() => {
            setDone(true);
            setOpen(false);
          }}
        />
      )}
    </>
  );
}
