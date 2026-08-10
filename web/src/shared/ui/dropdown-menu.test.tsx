import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { DropdownMenu } from "./dropdown-menu";

function renderMenu(items: Parameters<typeof DropdownMenu>[0]["items"]) {
  return render(
    <DropdownMenu
      aria-label="테스트 메뉴"
      trigger={(props) => (
        <button type="button" {...props}>
          열기
        </button>
      )}
      items={items}
    />,
  );
}

describe("DropdownMenu", () => {
  it("트리거를 누르기 전에는 메뉴가 안 보인다", () => {
    renderMenu([{ label: "항목", onSelect: vi.fn() }]);
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("트리거를 누르면 메뉴가 열리고 첫 항목에 포커스가 간다", async () => {
    const user = userEvent.setup();
    renderMenu([{ label: "항목 1", onSelect: vi.fn() }]);

    await user.click(screen.getByRole("button", { name: "열기" }));

    expect(screen.getByRole("menu", { name: "테스트 메뉴" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "항목 1" })).toHaveFocus();
  });

  it("onSelect 항목은 버튼으로, href 항목은 링크로 렌더한다", async () => {
    const user = userEvent.setup();
    renderMenu([
      { label: "버튼 항목", onSelect: vi.fn() },
      { label: "링크 항목", href: "/oauth2/authorization/google" },
    ]);
    await user.click(screen.getByRole("button", { name: "열기" }));

    expect(screen.getByRole("menuitem", { name: "버튼 항목" }).tagName).toBe("BUTTON");
    const link = screen.getByRole("menuitem", { name: "링크 항목" });
    expect(link.tagName).toBe("A");
    expect(link).toHaveAttribute("href", "/oauth2/authorization/google");
  });

  it("onSelect 항목을 누르면 콜백을 부르고 메뉴를 닫는다", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderMenu([{ label: "항목", onSelect }]);
    await user.click(screen.getByRole("button", { name: "열기" }));

    await user.click(screen.getByRole("menuitem", { name: "항목" }));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("Escape로 메뉴를 닫는다", async () => {
    const user = userEvent.setup();
    renderMenu([{ label: "항목", onSelect: vi.fn() }]);
    await user.click(screen.getByRole("button", { name: "열기" }));

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("화살표 키로 항목 사이를 이동한다", async () => {
    const user = userEvent.setup();
    renderMenu([
      { label: "첫째", onSelect: vi.fn() },
      { label: "둘째", onSelect: vi.fn() },
    ]);
    await user.click(screen.getByRole("button", { name: "열기" }));

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("menuitem", { name: "둘째" })).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(screen.getByRole("menuitem", { name: "첫째" })).toHaveFocus();
  });
});
