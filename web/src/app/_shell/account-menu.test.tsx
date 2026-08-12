import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AccountMenu } from "./account-menu";

const { logout, withdraw, refresh } = vi.hoisted(() => ({
  logout: vi.fn(),
  withdraw: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("@/entities/user-session/api", () => ({ logout, withdraw }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh, back: vi.fn() }),
}));

describe("계정 메뉴", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("닉네임을 트리거로 보여준다", () => {
    render(<AccountMenu nickname="당첨기원" />);
    expect(screen.getByRole("button", { name: "당첨기원님" })).toBeInTheDocument();
  });

  it("로그아웃을 누르면 성공 시 새로고침한다", async () => {
    const user = userEvent.setup();
    logout.mockResolvedValue(true);

    render(<AccountMenu nickname="당첨기원" />);
    await user.click(screen.getByRole("button", { name: "당첨기원님" }));
    await user.click(screen.getByRole("menuitem", { name: "로그아웃" }));

    await waitFor(() => expect(logout).toHaveBeenCalled());
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it("로그아웃 실패 시 오류 문구를 보여주고 새로고침하지 않는다", async () => {
    const user = userEvent.setup();
    logout.mockResolvedValue(false);

    render(<AccountMenu nickname="당첨기원" />);
    await user.click(screen.getByRole("button", { name: "당첨기원님" }));
    await user.click(screen.getByRole("menuitem", { name: "로그아웃" }));

    expect(
      await screen.findByText("로그아웃에 실패했습니다. 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("H-02: 메뉴에 회원 탈퇴 항목이 있다", async () => {
    const user = userEvent.setup();
    render(<AccountMenu nickname="당첨기원" />);
    await user.click(screen.getByRole("button", { name: "당첨기원님" }));

    expect(screen.getByRole("menuitem", { name: "회원 탈퇴" })).toBeInTheDocument();
  });

  it("H-02: 회원 탈퇴를 눌러도 확인 전에는 withdraw를 호출하지 않는다", async () => {
    const user = userEvent.setup();
    render(<AccountMenu nickname="당첨기원" />);
    await user.click(screen.getByRole("button", { name: "당첨기원님" }));
    await user.click(screen.getByRole("menuitem", { name: "회원 탈퇴" }));

    expect(screen.getByRole("dialog", { name: "정말 탈퇴할까요?" })).toBeInTheDocument();
    expect(withdraw).not.toHaveBeenCalled();
  });

  it("H-02: 취소를 누르면 withdraw를 호출하지 않고 다이얼로그를 닫는다", async () => {
    const user = userEvent.setup();
    render(<AccountMenu nickname="당첨기원" />);
    await user.click(screen.getByRole("button", { name: "당첨기원님" }));
    await user.click(screen.getByRole("menuitem", { name: "회원 탈퇴" }));
    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(withdraw).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("H-02: 확인하면 withdraw를 정확히 1회 호출하고 성공 시 새로고침한다", async () => {
    const user = userEvent.setup();
    withdraw.mockResolvedValue(true);

    render(<AccountMenu nickname="당첨기원" />);
    await user.click(screen.getByRole("button", { name: "당첨기원님" }));
    await user.click(screen.getByRole("menuitem", { name: "회원 탈퇴" }));
    await user.click(screen.getByRole("button", { name: "탈퇴" }));

    await waitFor(() => expect(withdraw).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("H-02: withdraw 실패 시 오류를 보여주고 다이얼로그를 유지하며 새로고침하지 않는다", async () => {
    const user = userEvent.setup();
    withdraw.mockResolvedValue(false);

    render(<AccountMenu nickname="당첨기원" />);
    await user.click(screen.getByRole("button", { name: "당첨기원님" }));
    await user.click(screen.getByRole("menuitem", { name: "회원 탈퇴" }));
    await user.click(screen.getByRole("button", { name: "탈퇴" }));

    expect(
      await screen.findByText("탈퇴에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "정말 탈퇴할까요?" })).toBeInTheDocument();
    expect(refresh).not.toHaveBeenCalled();
  });
});
