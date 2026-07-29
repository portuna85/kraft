import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Tabs } from "@/ui/primitives/tabs";

const ITEMS = [
  { value: "latest", label: "최신" },
  { value: "weekly", label: "이번 주 인기" },
] as const;

describe("Tabs 프리미티브", () => {
  it("role=tablist와 각 탭의 role=tab, aria-controls를 렌더링한다", () => {
    render(<Tabs items={ITEMS} value="latest" onChange={() => {}} panelIdPrefix="feed" />);
    expect(screen.getByRole("tablist")).toBeInTheDocument();
    const tab = screen.getByRole("tab", { name: "최신" });
    expect(tab).toHaveAttribute("aria-controls", "feed-panel-latest");
    expect(tab).toHaveAttribute("id", "feed-tab-latest");
  });

  it("현재 값에 aria-selected=true를 준다", () => {
    render(<Tabs items={ITEMS} value="weekly" onChange={() => {}} panelIdPrefix="feed" />);
    expect(screen.getByRole("tab", { name: "이번 주 인기" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "최신" })).toHaveAttribute("aria-selected", "false");
  });

  it("클릭 시 onChange(value)를 호출한다", () => {
    const onChange = vi.fn();
    render(<Tabs items={ITEMS} value="latest" onChange={onChange} panelIdPrefix="feed" />);
    screen.getByRole("tab", { name: "이번 주 인기" }).click();
    expect(onChange).toHaveBeenCalledWith("weekly");
  });

  it("ArrowLeft로 이전 탭을 선택한다(처음에서는 마지막으로 순환)", () => {
    const onChange = vi.fn();
    render(<Tabs items={ITEMS} value="latest" onChange={onChange} panelIdPrefix="feed" />);
    fireEvent.keyDown(screen.getByRole("tab", { name: "최신" }), { key: "ArrowLeft" });
    expect(onChange).toHaveBeenCalledWith("weekly");
    expect(screen.getByRole("tab", { name: "이번 주 인기" })).toHaveFocus();
  });
});
