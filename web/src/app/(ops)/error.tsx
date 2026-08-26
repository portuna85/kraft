"use client";

import { useEffect } from "react";

import { ApiError } from "@/shared/api/error";
import { reportClientError } from "@/shared/lib/report-client-error";
import { Button } from "@/shared/ui/button";

/**
 * 운영 셸 오류 경계
 *
 * FE-REL-01(docs/improvement.md): 이 파일이 없으면 `/ops` 렌더 중 throw가 루트
 * `global-error.tsx`까지 올라가 `<html>`을 통째로 교체한다 — 하필 사고 대응 중에
 * 쓰는 화면이 셸도 스타일도 없는 최소 화면이 된다.
 *
 * `(public)/error.tsx`와 달리 이 화면은 호스트 게이트(`proxy.ts`) 뒤의 운영자만
 * 본다 — 그래서 일반 사용자용 안내문 대신 원인 메시지와 상관관계 ID를 그대로
 * 보여준다. **이 파일은 `proxy.ts`의 호스트 게이트보다 먼저 반응하지 않는다** —
 * 게이트는 `/ops` 요청을 Next 라우팅에 도달하기 전에 `/not-found`로 rewrite하므로,
 * 이 error 경계는 게이트를 통과한 요청에서 실제 렌더 오류가 났을 때만 열린다.
 */
export default function OpsError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    reportClientError(error, "ops");
  }, [error]);

  const requestId = error instanceof ApiError ? error.requestId : null;

  return (
    <div className="prose stack">
      <h1>운영 콘솔에서 오류가 발생했습니다</h1>
      <p>{error.message}</p>
      {requestId && (
        <p>
          요청 ID: <code>{requestId}</code>
        </p>
      )}
      {error.digest && (
        <p>
          진단 ID: <code>{error.digest}</code>
        </p>
      )}
      <p>
        <Button onClick={reset}>다시 시도</Button>
      </p>
    </div>
  );
}
