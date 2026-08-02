import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { SegmentedControl } from "@/ui/primitives/segmented-control";
import { Tabs } from "@/ui/primitives/tabs";

const TAB_ITEMS = [
  { value: "first", label: "첫 탭" },
  { value: "disabled", label: "사용 안 함", disabled: true },
  { value: "last", label: "마지막 탭" },
] as const;

const OPTIONS = [
  { value: "first", label: "첫 옵션" },
  { value: "disabled", label: "사용 안 함", disabled: true },
  { value: "last", label: "마지막 옵션" },
] as const;

describe("프리미티브 Home/End 키 탐색", () => {
  it("Tabs는 비활성 탭을 건너뛰고 첫·마지막 탭을 선택한다", () => {
    const onChange = vi.fn();
    render(<Tabs items={TAB_ITEMS} value="first" onChange={onChange} panelIdPrefix="test" />);

    fireEvent.keyDown(screen.getByRole("tab", { name: "첫 탭" }), { key: "End" });
    expect(onChange).toHaveBeenLastCalledWith("last");
    expect(screen.getByRole("tab", { name: "마지막 탭" })).toHaveFocus();

    fireEvent.keyDown(screen.getByRole("tab", { name: "마지막 탭" }), { key: "Home" });
    expect(onChange).toHaveBeenLastCalledWith("first");
    expect(screen.getByRole("tab", { name: "첫 탭" })).toHaveFocus();
  });

  it("SegmentedControl은 비활성 옵션을 건너뛰고 첫·마지막 옵션을 선택한다", () => {
    const onChange = vi.fn();
    render(<SegmentedControl options={OPTIONS} value="first" onChange={onChange} aria-label="옵션" />);

    fireEvent.keyDown(screen.getByRole("radio", { name: "첫 옵션" }), { key: "End" });
    expect(onChange).toHaveBeenLastCalledWith("last");
    expect(screen.getByRole("radio", { name: "마지막 옵션" })).toHaveFocus();

    fireEvent.keyDown(screen.getByRole("radio", { name: "마지막 옵션" }), { key: "Home" });
    expect(onChange).toHaveBeenLastCalledWith("first");
    expect(screen.getByRole("radio", { name: "첫 옵션" })).toHaveFocus();
  });
});
