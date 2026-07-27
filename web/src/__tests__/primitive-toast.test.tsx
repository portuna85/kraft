import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Toast } from "@/ui/primitives/toast";

describe("Toast 프리미티브", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("메시지를 aria-live 영역으로 렌더링한다", () => {
    render(<Toast tone="success" message="저장했습니다" politeness="polite" onDismiss={() => {}} />);
    const toast = screen.getByRole("status");
    expect(toast).toHaveAttribute("aria-live", "polite");
    expect(toast).toHaveTextContent("저장했습니다");
  });

  it("오류는 assertive로 알린다", () => {
    render(<Toast tone="danger" message="실패했습니다" politeness="assertive" onDismiss={() => {}} />);
    expect(screen.getByRole("status")).toHaveAttribute("aria-live", "assertive");
  });

  it("durationMs가 지나면 onDismiss를 자동 호출한다", () => {
    const onDismiss = vi.fn();
    render(<Toast tone="neutral" message="알림" politeness="polite" durationMs={3000} onDismiss={onDismiss} />);
    expect(onDismiss).not.toHaveBeenCalled();
    vi.advanceTimersByTime(3000);
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it("닫기 버튼 클릭 시 onDismiss를 호출한다", () => {
    const onDismiss = vi.fn();
    render(<Toast tone="neutral" message="알림" politeness="polite" onDismiss={onDismiss} />);
    screen.getByRole("button", { name: "닫기" }).click();
    expect(onDismiss).toHaveBeenCalledOnce();
  });
});
