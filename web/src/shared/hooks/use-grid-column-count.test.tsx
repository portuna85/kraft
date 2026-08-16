import { act, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useGridColumnCount } from "./use-grid-column-count";

// jsdom은 실제 CSS 그리드 레이아웃을 계산하지 않으므로 getComputedStyle과
// ResizeObserver를 직접 모킹한다 — ad-unit.test.tsx의 ResizeObserver 모킹과 같은 패턴.
let mockColumnCount = 3;
let lastObserverCallback: ResizeObserverCallback | null = null;

class MockResizeObserver {
  #callback: ResizeObserverCallback;
  constructor(callback: ResizeObserverCallback) {
    this.#callback = callback;
    lastObserverCallback = callback;
  }
  observe() {
    this.#callback([] as unknown as ResizeObserverEntry[], this as unknown as ResizeObserver);
  }
  unobserve() {}
  disconnect() {}
}

function Grid() {
  const [ref, columns] = useGridColumnCount<HTMLDivElement>(7);
  return (
    <div ref={ref} data-testid="grid">
      <output aria-label="열 수">{columns}</output>
    </div>
  );
}

describe("useGridColumnCount", () => {
  const realGetComputedStyle = window.getComputedStyle;

  beforeEach(() => {
    lastObserverCallback = null;
    vi.stubGlobal("ResizeObserver", MockResizeObserver);
    window.getComputedStyle = ((el: Element) =>
      el instanceof HTMLElement && el.dataset.testid === "grid"
        ? ({ gridTemplateColumns: Array(mockColumnCount).fill("44px").join(" ") } as CSSStyleDeclaration)
        : realGetComputedStyle(el)) as typeof window.getComputedStyle;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.getComputedStyle = realGetComputedStyle;
  });

  it("마운트 시 실측 열 수로 갱신한다(하드코딩 폴백을 쓰지 않는다)", () => {
    mockColumnCount = 21;
    const { getByLabelText } = render(<Grid />);
    expect(getByLabelText("열 수")).toHaveTextContent("21");
  });

  it("컨테이너 크기가 바뀌면(ResizeObserver 콜백) 열 수를 다시 잰다", () => {
    mockColumnCount = 8;
    const { getByLabelText } = render(<Grid />);
    expect(getByLabelText("열 수")).toHaveTextContent("8");

    // 뷰포트가 좁아져 실제 렌더 열 수가 바뀌었다고 가정 — ResizeObserver 콜백을
    // 다시 호출해 재측정이 실제로 상태를 갱신하는지 확인한다.
    mockColumnCount = 6;
    act(() => {
      lastObserverCallback?.([] as unknown as ResizeObserverEntry[], {} as ResizeObserver);
    });

    expect(getByLabelText("열 수")).toHaveTextContent("6");
  });
});
