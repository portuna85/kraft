"use client";

import { useEffect } from "react";

import { reportClientError } from "@/shared/lib/report-client-error";

/**
 * 세션 셸 오류 경계 — improvement_fe.md §7.6
 *
 * 세션 관련 실패를 "로그인 안 됨"으로 축약하지 않는다(불변식 I-3). 축약하면
 * activeProviders가 비어 로그인 링크마저 사라지고 복구 경로가 없어진다 —
 * 실제로 한 번 일어난 사고다(레거시 FE-005).
 */
export default function SessionError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    reportClientError(error, "session");
  }, [error]);

  return (
    <div className="prose stack">
      <h1>화면을 불러오지 못했습니다</h1>
      <p>
        로그인 상태와는 별개의 문제일 수 있습니다. 다시 시도해도 같으면 페이지를 새로 고쳐 주세요.
      </p>
      <p>
        <button type="button" onClick={reset}>
          다시 시도
        </button>
      </p>
    </div>
  );
}
