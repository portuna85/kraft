import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider, useToast } from "./toast";

const MIN_VISIBLE_MS = 6_000;

function ShowButton({ message }: { message: string }) {
  const { show } = useToast();
  return (
    <button type="button" onClick={() => show(message)}>
      알림 표시
    </button>
  );
}

function renderProvider(children: React.ReactNode) {
  return render(<ToastProvider>{children}</ToastProvider>);
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

describe("ToastProvider / useToast", () => {
  it("show를 호출하면 토스트가 나타난다", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderProvider(<ShowButton message="저장됐습니다." />);

    await user.click(screen.getByRole("button", { name: "알림 표시" }));

    expect(screen.getByText("저장됐습니다.")).toBeInTheDocument();
  });

  it("최소 노출 시간(6초) 전에는 사라지지 않는다", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderProvider(<ShowButton message="저장됐습니다." />);
    await user.click(screen.getByRole("button", { name: "알림 표시" }));

    act(() => {
      vi.advanceTimersByTime(MIN_VISIBLE_MS - 100);
    });

    expect(screen.getByText("저장됐습니다.")).toBeInTheDocument();
  });

  it("최소 노출 시간이 지나면 자동으로 사라진다", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderProvider(<ShowButton message="저장됐습니다." />);
    await user.click(screen.getByRole("button", { name: "알림 표시" }));

    act(() => {
      vi.advanceTimersByTime(MIN_VISIBLE_MS + 100);
    });

    expect(screen.queryByText("저장됐습니다.")).not.toBeInTheDocument();
  });

  it("여러 토스트를 동시에 띄우면 각각 독립적으로 사라진다", async () => {
    function TwoShows() {
      const { show } = useToast();
      return (
        <>
          <button type="button" onClick={() => show("첫째")}>
            첫째 표시
          </button>
          <button type="button" onClick={() => show("둘째")}>
            둘째 표시
          </button>
        </>
      );
    }
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderProvider(<TwoShows />);

    await user.click(screen.getByRole("button", { name: "첫째 표시" }));
    act(() => {
      vi.advanceTimersByTime(3_000);
    });
    await user.click(screen.getByRole("button", { name: "둘째 표시" }));

    expect(screen.getByText("첫째")).toBeInTheDocument();
    expect(screen.getByText("둘째")).toBeInTheDocument();

    // 첫째는 6초 시점(경과 3초+3초)에 사라지고 둘째는 아직 남아 있어야 한다.
    act(() => {
      vi.advanceTimersByTime(3_100);
    });
    expect(screen.queryByText("첫째")).not.toBeInTheDocument();
    expect(screen.getByText("둘째")).toBeInTheDocument();
  });

  it("ToastProvider 밖에서 useToast를 쓰면 에러를 던진다", () => {
    function Bare() {
      useToast();
      return null;
    }
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => render(<Bare />)).toThrow("useToast는 ToastProvider 안에서만 쓸 수 있습니다.");

    consoleError.mockRestore();
  });

  it("TD-013: 수명 중간에 onExpire 정체성이 바뀌어도(재렌더) 타이머가 리셋되거나 중복 발화하지 않는다", async () => {
    // ToastItem 내부의 onExpire는 ToastProvider가 매 렌더 새로 만드는 인라인 클로저다.
    // useEventCallback으로 감쌌으므로, 부모가 리렌더돼 onExpire 참조가 바뀌어도
    // 마운트 시점에 예약한 단 하나의 타이머만 살아 있어야 한다(리셋도, 중복 발화도 없음).
    let forceRerender: () => void = () => {};
    function Wrapper({ registerRerender }: { registerRerender: (fn: () => void) => void }) {
      const [, setTick] = useState(0);
      registerRerender(() => setTick((t) => t + 1));
      return (
        <ToastProvider>
          <ShowButton message="저장됐습니다." />
        </ToastProvider>
      );
    }
    const registerRerender = (fn: () => void) => {
      forceRerender = fn;
    };
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Wrapper registerRerender={registerRerender} />);
    await user.click(screen.getByRole("button", { name: "알림 표시" }));

    act(() => {
      vi.advanceTimersByTime(3_000);
    });
    act(() => forceRerender());
    act(() => forceRerender());

    // 타이머가 리셋됐다면 이 시점(경과 3초+3.1초=6.1초, 리셋 기준으로는 3.1초)엔 아직 안 사라져야
    // 하지만 리셋되지 않았다면 원래 6초 시점에 이미 사라졌어야 한다.
    act(() => {
      vi.advanceTimersByTime(3_100);
    });
    expect(screen.queryByText("저장됐습니다.")).not.toBeInTheDocument();
  });
});
