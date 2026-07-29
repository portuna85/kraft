import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { SegmentedControl } from "@/ui/primitives/segmented-control";

const OPTIONS = [
  { value: "random", label: "무작위" },
  { value: "balanced", label: "균형 조합" },
  { value: "reduce", label: "공동 당첨 위험 완화" },
] as const;

describe("SegmentedControl 프리미티브", () => {
  it("role=radiogroup과 각 옵션의 role=radio를 렌더링한다", () => {
    render(
      <SegmentedControl options={OPTIONS} value="random" onChange={() => {}} aria-label="추천 모드" />
    );
    expect(screen.getByRole("radiogroup", { name: "추천 모드" })).toBeInTheDocument();
    expect(screen.getAllByRole("radio")).toHaveLength(3);
  });

  it("현재 값에 aria-checked=true를 준다", () => {
    render(
      <SegmentedControl options={OPTIONS} value="balanced" onChange={() => {}} aria-label="추천 모드" />
    );
    expect(screen.getByRole("radio", { name: "균형 조합" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("radio", { name: "무작위" })).toHaveAttribute("aria-checked", "false");
  });

  it("클릭 시 onChange(value)를 호출한다", () => {
    const onChange = vi.fn();
    render(<SegmentedControl options={OPTIONS} value="random" onChange={onChange} aria-label="추천 모드" />);
    screen.getByRole("radio", { name: "균형 조합" }).click();
    expect(onChange).toHaveBeenCalledWith("balanced");
  });

  it("ArrowRight로 다음 옵션을 선택한다(마지막에서는 처음으로 순환)", () => {
    const onChange = vi.fn();
    render(<SegmentedControl options={OPTIONS} value="reduce" onChange={onChange} aria-label="추천 모드" />);
    const current = screen.getByRole("radio", { name: "공동 당첨 위험 완화" });
    fireEvent.keyDown(current, { key: "ArrowRight" });
    expect(onChange).toHaveBeenCalledWith("random");
    expect(screen.getByRole("radio", { name: "무작위" })).toHaveFocus();
  });
});
