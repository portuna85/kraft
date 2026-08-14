"use client";

import { usePathname } from "next/navigation";
import { useReportWebVitals } from "next/web-vitals";

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
        release: "",
      }),
    );
  });

  return null;
}
