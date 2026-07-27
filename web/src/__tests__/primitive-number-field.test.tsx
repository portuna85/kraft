import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { NumberField } from "@/ui/primitives/number-field";

describe("NumberField 프리미티브", () => {
  it("빈 값이면 onChange(null)을 호출한다", () => {
    const onChange = vi.fn();
    render(<NumberField id="count" label="개수" value={5} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("개수"), { target: { value: "" } });
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("숫자 입력 시 onChange(number)를 호출한다", () => {
    const onChange = vi.fn();
    render(<NumberField id="count" label="개수" value={null} onChange={onChange} />);
    fireEvent.change(screen.getByLabelText("개수"), { target: { value: "7" } });
    expect(onChange).toHaveBeenCalledWith(7);
  });

  it("min/max/step을 그대로 전달한다", () => {
    render(<NumberField id="count" label="개수" value={1} onChange={() => {}} min={1} max={10} step={1} />);
    const input = screen.getByLabelText("개수");
    expect(input).toHaveAttribute("min", "1");
    expect(input).toHaveAttribute("max", "10");
    expect(input).toHaveAttribute("step", "1");
  });
});
