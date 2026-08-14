import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useKeyboardOpen } from "./use-keyboard-open";

class FakeVisualViewport extends EventTarget {
  width = 390;
  height = 800;
}

const originalViewport = window.visualViewport;

afterEach(() => {
  Object.defineProperty(window, "visualViewport", {
    configurable: true,
    value: originalViewport,
  });
});

describe("useKeyboardOpen", () => {
  it("visualViewport가 없으면 닫힘 상태를 유지한다", () => {
    Object.defineProperty(window, "visualViewport", { configurable: true, value: undefined });

    const { result } = renderHook(() => useKeyboardOpen());

    expect(result.current).toBe(false);
  });

  it("폭은 그대로이고 높이가 기준의 75% 미만이면 키보드 열림으로 본다", () => {
    const viewport = new FakeVisualViewport();
    Object.defineProperty(window, "visualViewport", { configurable: true, value: viewport });
    const { result } = renderHook(() => useKeyboardOpen());

    act(() => {
      viewport.height = 500;
      viewport.dispatchEvent(new Event("resize"));
    });

    expect(result.current).toBe(true);
  });

  it("폭 변경은 회전으로 보고 기준을 갱신하며 리스너를 정리한다", () => {
    const viewport = new FakeVisualViewport();
    const removeListener = vi.spyOn(viewport, "removeEventListener");
    Object.defineProperty(window, "visualViewport", { configurable: true, value: viewport });
    const { result, unmount } = renderHook(() => useKeyboardOpen());

    act(() => {
      viewport.height = 500;
      viewport.dispatchEvent(new Event("resize"));
    });
    expect(result.current).toBe(true);

    act(() => {
      viewport.width = 800;
      viewport.height = 600;
      viewport.dispatchEvent(new Event("resize"));
    });
    expect(result.current).toBe(false);

    unmount();
    expect(removeListener).toHaveBeenCalledWith("resize", expect.any(Function));
  });
});
