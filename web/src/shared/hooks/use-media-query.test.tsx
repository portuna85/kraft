import { act, render } from "@testing-library/react";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useMediaQuery } from "./use-media-query";

/**
 * FE-PERF-02 회귀 방지: subscribe/getSnapshot이 매 렌더 새 함수 정체성을 가지면
 * useSyncExternalStore가 리렌더마다 addEventListener/removeEventListener를 다시
 * 부른다. 같은 mql 인스턴스를 반환하는 matchMedia mock으로 그 호출 횟수를 직접 센다.
 */
const addEventListener = vi.fn();
const removeEventListener = vi.fn();

function mockMatchMedia() {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener,
      removeEventListener,
      dispatchEvent: vi.fn(),
    })),
  });
}

function Probe({ rerenderCount }: { rerenderCount: number }) {
  useMediaQuery("(min-width: 768px)");
  return <span>{rerenderCount}</span>;
}

function Harness() {
  const [count, setCount] = useState(0);
  return (
    <div>
      <Probe rerenderCount={count} />
      <button onClick={() => setCount((c) => c + 1)}>rerender</button>
    </div>
  );
}

describe("useMediaQuery — 재구독 방지", () => {
  beforeEach(() => {
    mockMatchMedia();
    addEventListener.mockClear();
    removeEventListener.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("같은 쿼리로 10번 리렌더돼도 addEventListener는 1회만 호출된다", () => {
    const { getByRole } = render(<Harness />);
    const button = getByRole("button");

    for (let i = 0; i < 10; i += 1) {
      act(() => {
        button.click();
      });
    }

    expect(addEventListener).toHaveBeenCalledTimes(1);
    expect(removeEventListener).not.toHaveBeenCalled();
  });

  it("컴포넌트 unmount 시 구독을 해제한다", () => {
    const { unmount } = render(<Harness />);

    unmount();

    expect(removeEventListener).toHaveBeenCalledTimes(1);
  });
});
