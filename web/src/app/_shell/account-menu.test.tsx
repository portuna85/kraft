import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AccountMenu } from "./account-menu";

const { logout, refresh } = vi.hoisted(() => ({
  logout: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("@/entities/user-session/api", () => ({ logout }));
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
});
