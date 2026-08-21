import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { WebVitalsReporter } from "./web-vitals-reporter";

const { reportCallback } = vi.hoisted(() => ({
  reportCallback: { current: null as ((metric: unknown) => void) | null },
}));

vi.mock("next/navigation", () => ({ usePathname: () => "/frequency" }));
vi.mock("next/web-vitals", () => ({
  useReportWebVitals: (callback: (metric: unknown) => void) => {
    reportCallback.current = callback;
  },
}));

function metric(overrides: Partial<Record<string, unknown>> = {}) {
  return { name: "LCP", value: 1234.5, rating: "good", id: "v1-abc", ...overrides };
}

afterEach(() => {
  vi.unstubAllGlobals();
  reportCallback.current = null;
});

describe("WebVitalsReporter", () => {
  it("추적 대상 지표(LCP·INP·CLS)는 sendBeacon으로 /api/vitals에 보낸다", () => {
    const sendBeacon = vi.fn();
    vi.stubGlobal("navigator", { sendBeacon });
    vi.stubGlobal("innerWidth", 1280);

    render(<WebVitalsReporter />);
    reportCallback.current?.(metric());

    expect(sendBeacon).toHaveBeenCalledTimes(1);
    const [url, body] = sendBeacon.mock.calls[0] as [string, string];
    expect(url).toBe("/api/vitals");
    expect(JSON.parse(body)).toMatchObject({
      name: "LCP",
      value: 1234.5,
      rating: "good",
      route: "/frequency",
      deviceClass: "desktop",
      layoutClass: "desktop-nav",
    });
  });

  it("추적 대상이 아닌 지표(FCP 등)는 보내지 않는다", () => {
    const sendBeacon = vi.fn();
    vi.stubGlobal("navigator", { sendBeacon });
    vi.stubGlobal("innerWidth", 1280);

    render(<WebVitalsReporter />);
    reportCallback.current?.(metric({ name: "FCP" }));

    expect(sendBeacon).not.toHaveBeenCalled();
  });

  it("뷰포트 폭에 따라 deviceClass를 분류한다", () => {
    const sendBeacon = vi.fn();
    vi.stubGlobal("navigator", { sendBeacon });
    vi.stubGlobal("innerWidth", 375);

    render(<WebVitalsReporter />);
    reportCallback.current?.(metric({ name: "CLS" }));

    const body = JSON.parse((sendBeacon.mock.calls[0] as [string, string])[1]);
    expect(body.deviceClass).toBe("mobile");
  });

  /**
   * RSP-38(docs/improvement.md): deviceClass(640/1024px)와 실제 셸 전환
   * (1152px, BP.desktopNav)이 어긋나 1024~1151px 탭바 UI가 desktop으로
   * 집계됐다. 예전에는 375·1280px 두 값만 테스트돼 있어 어느 경계도 실측되지
   * 않았다 — 여섯 경계 폭을 표로 고정한다.
   */
  describe("경계 폭에서 deviceClass·layoutClass가 정확히 갈린다", () => {
    const CASES: {
      width: number;
      deviceClass: "mobile" | "tablet" | "desktop";
      layoutClass: "compact" | "desktop-nav";
    }[] = [
      { width: 639, deviceClass: "mobile", layoutClass: "compact" },
      { width: 640, deviceClass: "tablet", layoutClass: "compact" },
      { width: 1023, deviceClass: "tablet", layoutClass: "compact" },
      { width: 1024, deviceClass: "desktop", layoutClass: "compact" },
      { width: 1151, deviceClass: "desktop", layoutClass: "compact" },
      { width: 1152, deviceClass: "desktop", layoutClass: "desktop-nav" },
    ];

    for (const { width, deviceClass, layoutClass } of CASES) {
      it(`${width}px -> deviceClass=${deviceClass}, layoutClass=${layoutClass}`, () => {
        const sendBeacon = vi.fn();
        vi.stubGlobal("navigator", { sendBeacon });
        vi.stubGlobal("innerWidth", width);

        render(<WebVitalsReporter />);
        reportCallback.current?.(metric({ name: "CLS" }));

        const body = JSON.parse((sendBeacon.mock.calls[0] as [string, string])[1]);
        expect(body.deviceClass).toBe(deviceClass);
        expect(body.layoutClass).toBe(layoutClass);
      });
    }
  });

  it("sendBeacon이 없으면 fetch(keepalive)로 대체한다", () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("navigator", {});
    vi.stubGlobal("fetch", fetchSpy);
    vi.stubGlobal("innerWidth", 1280);

    render(<WebVitalsReporter />);
    reportCallback.current?.(metric({ name: "INP" }));

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/vitals",
      expect.objectContaining({ method: "POST", keepalive: true }),
    );
  });
});
