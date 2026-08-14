import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { Tabs, type TabItem } from "./tabs";

const ITEMS: readonly TabItem<"latest" | "popular">[] = [
  { value: "latest", label: "최신", panel: <p>최신 글</p> },
  { value: "popular", label: "인기", panel: <p>인기 글</p> },
];

function Harness() {
  const [value, setValue] = useState<"latest" | "popular">("latest");
  return <Tabs items={ITEMS} value={value} onChange={setValue} aria-label="게시글 정렬" />;
}

describe("Tabs", () => {
  it("선택 탭과 패널을 접근성 속성으로 연결한다", () => {
    render(<Harness />);

    const tab = screen.getByRole("tab", { name: "최신", selected: true });
    const panel = screen.getByRole("tabpanel", { name: "최신" });
    expect(tab).toHaveAttribute("aria-controls", panel.id);
    expect(panel).toHaveAttribute("aria-labelledby", tab.id);
    expect(tab).toHaveAttribute("tabindex", "0");
    expect(screen.getByRole("tab", { name: "인기" })).toHaveAttribute("tabindex", "-1");
  });

  it("클릭하면 선택과 패널 내용을 함께 바꾼다", async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole("tab", { name: "인기" }));

    expect(screen.getByRole("tab", { name: "인기", selected: true })).toBeInTheDocument();
    expect(screen.getByRole("tabpanel")).toHaveTextContent("인기 글");
  });

  it("좌우 화살표가 양 끝에서 순환한다", () => {
    render(<Harness />);
    const tabList = screen.getByRole("tablist", { name: "게시글 정렬" });

    fireEvent.keyDown(tabList, { key: "ArrowLeft" });
    expect(screen.getByRole("tab", { name: "인기", selected: true })).toBeInTheDocument();

    fireEvent.keyDown(tabList, { key: "ArrowRight" });
    expect(screen.getByRole("tab", { name: "최신", selected: true })).toBeInTheDocument();
  });
});
