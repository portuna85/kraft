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
