import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { DropdownMenu } from "./dropdown-menu";

function renderMenu(onSelect = vi.fn()) {
  render(
    <>
      <DropdownMenu
        trigger={({ onClick, "aria-expanded": expanded }) => (
          <button type="button" onClick={onClick} aria-expanded={expanded}>
            메뉴 열기
          </button>
        )}
        items={[
          { label: "프로필", href: "/profile" },
          { label: "로그아웃", onSelect },
        ]}
        aria-label="계정"
      />
      <button type="button">바깥</button>
    </>,
  );
  return onSelect;
}

describe("DropdownMenu", () => {
  it("열리면 첫 항목에 포커스하고 화살표·Home·End로 이동한다", async () => {
    renderMenu();
    await userEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));

    const profile = screen.getByRole("menuitem", { name: "프로필" });
    const logout = screen.getByRole("menuitem", { name: "로그아웃" });
    await waitFor(() => expect(profile).toHaveFocus());
    fireEvent.keyDown(screen.getByRole("menu", { name: "계정" }), { key: "ArrowDown" });
    expect(logout).toHaveFocus();
    fireEvent.keyDown(screen.getByRole("menu", { name: "계정" }), { key: "Home" });
    expect(profile).toHaveFocus();
    fireEvent.keyDown(screen.getByRole("menu", { name: "계정" }), { key: "End" });
    expect(logout).toHaveFocus();
  });

  it("동작 항목을 선택하면 메뉴를 닫고 콜백을 한 번 호출한다", async () => {
    const onSelect = renderMenu();
    await userEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));

    await userEvent.click(screen.getByRole("menuitem", { name: "로그아웃" }));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  // I-33: 회원 탈퇴 같은 파괴적 항목이 일상 항목과 시각적으로 구분되지 않았다.
  it("separatorBefore를 준 항목 위에 구분선을 그린다", async () => {
    render(
      <DropdownMenu
        trigger={({ onClick, "aria-expanded": expanded }) => (
          <button type="button" onClick={onClick} aria-expanded={expanded}>
            메뉴 열기
          </button>
        )}
        items={[
          { label: "로그아웃", onSelect: vi.fn() },
          { label: "회원 탈퇴", tone: "danger", separatorBefore: true, onSelect: vi.fn() },
        ]}
        aria-label="계정"
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));

    expect(screen.getByRole("separator")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "회원 탈퇴" })).toBeInTheDocument();
  });

  it("Escape와 바깥 포인터 입력으로 닫고 리스너를 제거한다", async () => {
    renderMenu();
    const trigger = screen.getByRole("button", { name: "메뉴 열기" });
    await userEvent.click(trigger);
    fireEvent.keyDown(screen.getByRole("menu", { name: "계정" }), { key: "Escape" });
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();

    await userEvent.click(trigger);
    fireEvent.pointerDown(screen.getByRole("button", { name: "바깥" }));
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  // KF-18(docs/improvement.md): 닫힐 때 포커스가 <body>로 떨어져 로그아웃·로그인
  // 메뉴를 쓰는 키보드 사용자가 매번 문서 처음으로 되돌아갔다.
  it("KF-18: Escape로 닫으면 포커스가 트리거로 돌아간다", async () => {
    renderMenu();
    const trigger = screen.getByRole("button", { name: "메뉴 열기" });
    await userEvent.click(trigger);

    fireEvent.keyDown(screen.getByRole("menu", { name: "계정" }), { key: "Escape" });

    expect(trigger).toHaveFocus();
  });

  it("KF-18: 항목을 선택해 닫으면 포커스가 트리거로 돌아간다", async () => {
    const onSelect = renderMenu();
    const trigger = screen.getByRole("button", { name: "메뉴 열기" });
    await userEvent.click(trigger);

    await userEvent.click(screen.getByRole("menuitem", { name: "로그아웃" }));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(trigger).toHaveFocus();
  });

  it("KF-18: 바깥 포인터 입력으로 닫으면 포커스가 트리거로 돌아간다", async () => {
    renderMenu();
    const trigger = screen.getByRole("button", { name: "메뉴 열기" });
    await userEvent.click(trigger);

    fireEvent.pointerDown(screen.getByRole("button", { name: "바깥" }));

    expect(trigger).toHaveFocus();
  });

  it("KF-18: 트리거에 aria-haspopup=menu와, 열린 메뉴를 가리키는 aria-controls가 있다", async () => {
    render(
      <DropdownMenu
        trigger={(props) => (
          <button type="button" {...props}>
            메뉴 열기
          </button>
        )}
        items={[{ label: "로그아웃", onSelect: vi.fn() }]}
        aria-label="계정"
      />,
    );
    const trigger = screen.getByRole("button", { name: "메뉴 열기" });
    expect(trigger).toHaveAttribute("aria-haspopup", "menu");

    await userEvent.click(trigger);

    const menu = screen.getByRole("menu", { name: "계정" });
    expect(trigger.getAttribute("aria-controls")).toBe(menu.id);
  });
});
