import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { SegmentedControl, Switch } from "./toggle";

describe("Switch", () => {
  it("현재 상태를 switch 의미로 노출하고 클릭 시 반대 값을 전달한다", async () => {
    const onChange = vi.fn();
    render(<Switch checked={false} onChange={onChange} label="알림" />);

    const control = screen.getByRole("switch", { name: "알림" });
    expect(control).toHaveAttribute("aria-checked", "false");
    await userEvent.click(control);
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it("비활성 상태에서는 값을 바꾸지 않는다", async () => {
    const onChange = vi.fn();
    render(<Switch checked onChange={onChange} label="알림" disabled />);

    await userEvent.click(screen.getByRole("switch", { name: "알림" }));
    expect(onChange).not.toHaveBeenCalled();
  });
});

function SegmentedHarness() {
  const [value, setValue] = useState<"all" | "mine">("all");
  return (
    <SegmentedControl
      options={[
        { value: "all", label: "전체" },
        { value: "mine", label: "내 글" },
      ]}
      value={value}
      onChange={setValue}
      aria-label="게시글 범위"
    />
  );
}

describe("SegmentedControl", () => {
  it("라디오 그룹의 선택과 단일 탭 정지점을 유지한다", () => {
    render(<SegmentedHarness />);

    expect(screen.getByRole("radiogroup", { name: "게시글 범위" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "전체", checked: true })).toHaveAttribute(
      "tabindex",
      "0",
    );
    expect(screen.getByRole("radio", { name: "내 글" })).toHaveAttribute("tabindex", "-1");
  });

  it("화살표로 선택을 순환한다", () => {
    render(<SegmentedHarness />);
    const group = screen.getByRole("radiogroup", { name: "게시글 범위" });

    fireEvent.keyDown(group, { key: "ArrowLeft" });
    expect(screen.getByRole("radio", { name: "내 글", checked: true })).toBeInTheDocument();
    fireEvent.keyDown(group, { key: "ArrowRight" });
    expect(screen.getByRole("radio", { name: "전체", checked: true })).toBeInTheDocument();
  });
});
