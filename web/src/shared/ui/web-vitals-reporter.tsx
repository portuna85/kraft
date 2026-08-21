"use client";

import { usePathname } from "next/navigation";
import { useReportWebVitals } from "next/web-vitals";

import { BP } from "@/shared/config/breakpoints";

/**
 * Web Vitals 클라이언트 수집
 *
 * `/api/vitals`는 이미 있었지만(페이로드 스키마 검증까지) 그걸 실제로 부르는
 * 클라이언트 코드가 하나도 없어 수집 파이프라인이 죽어 있었다. 서버가 받는 3개
 * (`LCP`·`INP`·`CLS`)만 필터링해 보낸다 — 그 외 지표(FCP·TTFB 등)는 서버가
 * 애초에 거부한다.
 *
 * 전송은 `report-client-error.ts`와 같은 이유로 `sendBeacon` 우선이다 — 페이지
 * 이탈 중에도 전송이 보장되고 실패해도 던지지 않는다.
 */
const TRACKED_METRICS = new Set(["LCP", "INP", "CLS"]);

/** 640px·1024px는 이 저장소의 CSS 모듈들이 실제로 쓰는 반응형 분기점이다. */
function deviceClassOf(width: number): "mobile" | "tablet" | "desktop" {
  if (width < 640) return "mobile";
  if (width < 1024) return "tablet";
  return "desktop";
}

/**
 * RSP-38(docs/improvement.md): `deviceClass`는 640/1024px로 기기를 나누는데
 * 실제 셸 전환(탭바 ↔ 데스크톱 nav)은 `BP.desktopNav`(1152px)다. 그 결과
 * 1024~1151px에서는 탭바가 보이는 화면인데도 `deviceClass`가 `desktop`으로
 * 집계돼, 특정 레이아웃 회귀와 Web Vitals 변화를 현장 데이터로 연결할 수
 * 없었다. `deviceClass`는 분석 호환성 때문에 그대로 두고, 실제 셸 상태를
 * 정확히 반영하는 `layoutClass`를 별도로 추가한다 — 값은 `BP.desktopNav`에서
 * 직접 가져온다(breakpoints.ts:8-18 주석대로 CSS와 이 상수는 수동 동기화
 * 대상이다).
 */
function layoutClassOf(width: number): "compact" | "desktop-nav" {
  return width >= BP.desktopNav ? "desktop-nav" : "compact";
}

function send(body: string): void {
  if (typeof navigator.sendBeacon === "function") {
    navigator.sendBeacon("/api/vitals", body);
    return;
  }

  void fetch("/api/vitals", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
    keepalive: true,
  }).catch(() => undefined);
}

export function WebVitalsReporter() {
  const pathname = usePathname();

  useReportWebVitals((metric) => {
    if (!TRACKED_METRICS.has(metric.name)) return;

    send(
      JSON.stringify({
        name: metric.name,
        value: metric.value,
        rating: metric.rating,
        route: pathname,
        deviceClass: deviceClassOf(window.innerWidth),
        layoutClass: layoutClassOf(window.innerWidth),
        release: process.env.NEXT_PUBLIC_APP_VERSION ?? "",
      }),
    );
  });

  return null;
}
