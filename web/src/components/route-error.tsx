"use client";

import Link from "next/link";
import { useEffect } from "react";

// M-3: error.tsx는 클라이언트 컴포넌트라 서버 pino 파이프라인에 직접 쓸 수 없다.
// sendBeacon을 우선 쓴다 — 사용자가 reset()이나 다른 페이지로 즉시 이동해도 전송이
// 끊기지 않는다(web-vitals-reporter.tsx와 동일한 패턴).
function reportError(error: Error & { digest?: string }) {
  const payload = JSON.stringify({
    message: error.message,
    digest: error.digest,
    route: window.location.pathname,
  });

  if (navigator.sendBeacon) {
    navigator.sendBeacon("/api/client-error", payload);
  } else {
    fetch("/api/client-error", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: payload,
      keepalive: true,
    }).catch(() => {
      // 오류 보고 자체의 실패로 사용자가 더 막히면 안 된다 — 최선 노력.
    });
  }
}

export function RouteError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
    reportError(error);
  }, [error]);

  return (
    <section className="panel not-found">
      <p className="eyebrow">오류 발생</p>
      <h1 className="page-title">페이지를 불러오지 못했습니다</h1>
      <p className="page-subtitle">
        데이터를 가져오는 중 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.
        {error.digest ? ` (코드: ${error.digest})` : ""}
      </p>
      <div className="not-found-actions">
        <button onClick={reset}>다시 불러오기</button>
        <Link href="/" className="button secondary">홈으로 이동</Link>
      </div>
    </section>
  );
}
