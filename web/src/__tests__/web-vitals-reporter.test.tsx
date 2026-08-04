import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/react";
import { WebVitalsReporter } from "@/components/web-vitals-reporter";

const callbacks: Record<string, (metric: unknown) => void> = {};

vi.mock("web-vitals", () => ({
  onLCP: (cb: (metric: unknown) => void) => { callbacks.LCP = cb; },
  onINP: (cb: (metric: unknown) => void) => { callbacks.INP = cb; },
  onCLS: (cb: (metric: unknown) => void) => { callbacks.CLS = cb; },
}));

describe("WebVitalsReporter", () => {
  const originalWidth = window.innerWidth;
  const sendBeaconSpy = vi.fn().mockReturnValue(true);

  beforeEach(() => {
    for (const key of Object.keys(callbacks)) delete callbacks[key];
    sendBeaconSpy.mockClear();
    Object.defineProperty(navigator, "sendBeacon", { value: sendBeaconSpy, configurable: true });
    Object.defineProperty(window, "location", {
      value: { ...window.location, pathname: "/recommend" },
      configurable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(window, "innerWidth", { value: originalWidth, configurable: true });
  });

  it("마운트 시 LCP/INP/CLS 콜백을 각각 한 번씩 등록한다", async () => {
    render(<WebVitalsReporter />);

    await waitFor(() => {
      expect(callbacks.LCP).toBeInstanceOf(Function);
      expect(callbacks.INP).toBeInstanceOf(Function);
      expect(callbacks.CLS).toBeInstanceOf(Function);
    });
  });

  it("지표가 보고되면 개인정보 없이 route·deviceClass·release만 담아 sendBeacon으로 전송한다", async () => {
    render(<WebVitalsReporter />);
    await waitFor(() => expect(callbacks.LCP).toBeInstanceOf(Function));

    callbacks.LCP!({ name: "LCP", value: 1800.2, rating: "needs-improvement" });

    expect(sendBeaconSpy).toHaveBeenCalledTimes(1);
    const [url, body] = sendBeaconSpy.mock.calls[0]!;
    expect(url).toBe("/api/vitals");
    const parsed = JSON.parse(body as string);
    expect(parsed).toEqual({
      name: "LCP",
      value: 1800.2,
      rating: "needs-improvement",
      route: "/recommend",
      deviceClass: expect.any(String),
      release: "unknown",
    });
    expect(Object.keys(parsed).sort()).toEqual([
      "deviceClass",
      "name",
      "rating",
      "release",
      "route",
      "value",
    ]);
  });

  it("뷰포트 너비에 따라 mobile/tablet/desktop 중 하나로 deviceClass를 분류한다", async () => {
    render(<WebVitalsReporter />);
    await waitFor(() => expect(callbacks.CLS).toBeInstanceOf(Function));

    Object.defineProperty(window, "innerWidth", { value: 375, configurable: true });
    callbacks.CLS!({ name: "CLS", value: 0.05, rating: "good" });
    expect(JSON.parse(sendBeaconSpy.mock.calls[0]![1] as string).deviceClass).toBe("mobile");

    Object.defineProperty(window, "innerWidth", { value: 800, configurable: true });
    callbacks.CLS!({ name: "CLS", value: 0.05, rating: "good" });
    expect(JSON.parse(sendBeaconSpy.mock.calls[1]![1] as string).deviceClass).toBe("tablet");

    Object.defineProperty(window, "innerWidth", { value: 1440, configurable: true });
    callbacks.CLS!({ name: "CLS", value: 0.05, rating: "good" });
    expect(JSON.parse(sendBeaconSpy.mock.calls[2]![1] as string).deviceClass).toBe("desktop");
  });

  it("sendBeacon이 없으면 keepalive fetch로 대체 전송한다", async () => {
    Object.defineProperty(navigator, "sendBeacon", { value: undefined, configurable: true });
    const fetchSpy = vi.fn().mockResolvedValue({ ok: true });
    global.fetch = fetchSpy;

    render(<WebVitalsReporter />);
    await waitFor(() => expect(callbacks.INP).toBeInstanceOf(Function));
    callbacks.INP!({ name: "INP", value: 150, rating: "good" });

    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/vitals",
      expect.objectContaining({ method: "POST", keepalive: true })
    );
  });
});
