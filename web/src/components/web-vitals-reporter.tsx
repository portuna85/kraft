"use client";

import { useEffect } from "react";
import { onCLS, onINP, onLCP, type Metric } from "web-vitals";
import { BP } from "@/lib/breakpoints";

// F-06: 실사용자 측정(RUM)을 /api/vitals로 보낸다. 개인정보(IP, User-Agent, 쿠키,
// 식별자)는 절대 담지 않는다 — 완료 조건이 요구하는 라우트·디바이스 클래스·릴리스
// 세 차원만 붙인다. deviceClass는 새 기준을 만들지 않고 기존 반응형 브레이크포인트
// (lib/breakpoints.ts)를 그대로 재사용한다.
//
// LCP/CLS는 최초 내비게이션 기준으로 한 번만 확정되는 지표라 onLCP/onINP/onCLS는
// 마운트 시 한 번만 등록한다(라우트 변경마다 다시 등록하면 옵저버가 중복 생성된다).
// 라우트는 usePathname()으로 값을 미리 고정하지 않고, 보고 시점에
// window.location.pathname을 직접 읽는다 — 클라이언트 사이드 전환 후 INP가
// 늦게 보고돼도 "측정 당시의 실제 경로"를 기록하기 위함이다.
function deviceClass(): "mobile" | "tablet" | "desktop" {
  const width = window.innerWidth;
  if (width < BP.tablet) return "mobile";
  if (width < BP.desktop) return "tablet";
  return "desktop";
}

function send(metric: Metric) {
  const payload = JSON.stringify({
    name: metric.name,
    value: metric.value,
    rating: metric.rating,
    route: window.location.pathname,
    deviceClass: deviceClass(),
    release: process.env.NEXT_PUBLIC_RELEASE ?? "unknown",
  });

  if (navigator.sendBeacon) {
    navigator.sendBeacon("/api/vitals", payload);
  } else {
    fetch("/api/vitals", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: payload,
      keepalive: true,
    }).catch(() => {
      // 필드 계측은 최선 노력(best-effort)이다 — 전송 실패가 사용자 경험을 막으면 안 된다.
    });
  }
}

export function WebVitalsReporter() {
  useEffect(() => {
    onLCP(send);
    onINP(send);
    onCLS(send);
  }, []);

  return null;
}
