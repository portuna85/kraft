import { act, render } from "@testing-library/react";
import { createElement, useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { useEventCallback } from "./use-event-callback";

function Harness({
  onCall,
  registerTrigger,
}: {
  onCall: (value: number) => void;
  registerTrigger: (trigger: () => void) => void;
}) {
  const [value, setValue] = useState(0);
  const stable = useEventCallback(() => onCall(value));

  registerTrigger(() => {
    stable();
    setValue((v) => v + 1);
  });

  return createElement("div", null, value);
}

describe("useEventCallback", () => {
  it("재렌더 후에도 항상 최신 클로저를 호출한다", () => {
    const calls: number[] = [];
    let trigger: () => void = () => {};

    render(
      createElement(Harness, {
        onCall: (v) => calls.push(v),
        registerTrigger: (t) => {
          trigger = t;
        },
      }),
    );

    act(() => trigger());
    act(() => trigger());
    act(() => trigger());

    // 매 호출 시점에 value가 갱신된 뒤 다음 트리거를 호출하므로, stale closure였다면
    // 항상 0만 기록됐을 것 — 0, 1, 2가 기록돼야 최신 클로저를 호출한 것이다.
    expect(calls).toEqual([0, 1, 2]);
  });

  it("반환된 래퍼의 참조는 재렌더 간에 안정적이다", () => {
    const refs: unknown[] = [];

    function Probe() {
      const stable = useEventCallback(() => {});
      refs.push(stable);
      return null;
    }

    const { rerender } = render(createElement(Probe));
    rerender(createElement(Probe));
    rerender(createElement(Probe));

    expect(refs).toHaveLength(3);
    expect(refs[0]).toBe(refs[1]);
    expect(refs[1]).toBe(refs[2]);
  });

  it("인자를 그대로 전달한다", () => {
    const received: Array<[number, string]> = [];

    function Probe({ trigger }: { trigger: (fn: (a: number, b: string) => void) => void }) {
      const stable = useEventCallback((a: number, b: string) => {
        received.push([a, b]);
      });
      trigger(stable);
      return null;
    }

    let call: ((a: number, b: string) => void) | null = null;
    render(
      createElement(Probe, {
        trigger: (fn) => {
          call = fn;
        },
      }),
    );

    act(() => call?.(42, "hello"));

    expect(received).toEqual([[42, "hello"]]);
  });

  it("동일한 stable 콜백이 여러 번 호출돼도 매번 최신 콜백 본문을 실행한다", () => {
    const spy = vi.fn();
    let renderCount = 0;
    const holder: { current: (() => void) | null } = { current: null };

    function Probe({ registerStable }: { registerStable: (fn: () => void) => void }) {
      renderCount++;
      const count = renderCount;
      const stable = useEventCallback(() => spy(count));
      registerStable(stable);
      return null;
    }

    const registerStable = (fn: () => void) => {
      holder.current = fn;
    };
    const { rerender } = render(createElement(Probe, { registerStable }));
    holder.current?.();
    rerender(createElement(Probe, { registerStable }));
    holder.current?.();

    expect(spy.mock.calls).toEqual([[1], [2]]);
  });
});
