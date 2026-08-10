import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { SegmentedControl } from "./segmented-control";

const OPTIONS = [
  { value: "locked", label: "고정 번호" },
  { value: "excluded", label: "제외 번호" },
] as const;

describe("SegmentedControl", () => {
  it("선택된 옵션만 aria-checked·roving tabindex를 갖는다", () => {
    render(
      <SegmentedControl
        aria-label="선택 모드"
        options={OPTIONS}
        value="locked"
        onChange={vi.fn()}
      />,
    );

    const locked = screen.getByRole("radio", { name: "고정 번호" });
    const excluded = screen.getByRole("radio", { name: "제외 번호" });
    expect(locked).toHaveAttribute("aria-checked", "true");
    expect(locked).toHaveAttribute("tabindex", "0");
    expect(excluded).toHaveAttribute("aria-checked", "false");
    expect(excluded).toHaveAttribute("tabindex", "-1");
  });

  it("클릭하면 onChange를 부른다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <SegmentedControl
        aria-label="선택 모드"
        options={OPTIONS}
        value="locked"
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole("radio", { name: "제외 번호" }));

    expect(onChange).toHaveBeenCalledWith("excluded");
  });

  it("오른쪽 화살표로 다음 옵션을 선택하고 포커스도 옮긴다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <SegmentedControl
        aria-label="선택 모드"
        options={OPTIONS}
        value="locked"
        onChange={onChange}
      />,
    );

    screen.getByRole("radio", { name: "고정 번호" }).focus();
    await user.keyboard("{ArrowRight}");

    expect(onChange).toHaveBeenCalledWith("excluded");
  });

  it("마지막에서 오른쪽 화살표를 누르면 처음으로 순환한다", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <SegmentedControl
        aria-label="선택 모드"
        options={OPTIONS}
        value="excluded"
        onChange={onChange}
      />,
    );

    screen.getByRole("radio", { name: "제외 번호" }).focus();
    await user.keyboard("{ArrowRight}");

    expect(onChange).toHaveBeenCalledWith("locked");
  });
});
