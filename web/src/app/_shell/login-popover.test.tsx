import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LoginPopover } from "./login-popover";

let mockPathname = "/frequency";
let mockSearch = "";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
  useSearchParams: () => new URLSearchParams(mockSearch),
}));

describe("로그인 팝오버", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    mockPathname = "/frequency";
    mockSearch = "";
  });

  it("기본은 로그인 버튼 하나만 보인다", () => {
    render(<LoginPopover />);
    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("누르면 두 provider가 전체 페이지 이동 링크로 나온다", async () => {
    const user = userEvent.setup();
    render(<LoginPopover />);

    await user.click(screen.getByRole("button", { name: "로그인" }));

    const google = screen.getByRole("menuitem", { name: "Google로 계속" });
    const naver = screen.getByRole("menuitem", { name: "Naver로 계속" });
    expect(google).toHaveAttribute("href", "/oauth2/authorization/google");
    expect(naver).toHaveAttribute("href", "/oauth2/authorization/naver");
  });

  it("provider를 누르면 현재 경로를 복귀용으로 저장한다 (§25.1)", async () => {
    const user = userEvent.setup();
    render(<LoginPopover />);

    await user.click(screen.getByRole("button", { name: "로그인" }));
    await user.click(screen.getByRole("menuitem", { name: "Google로 계속" }));

    expect(window.sessionStorage.getItem("kraft-return-to")).toBe("/frequency");
  });

  it("검색 쿼리가 있으면 함께 저장한다", async () => {
    mockSearch = "limit=100";
    const user = userEvent.setup();
    render(<LoginPopover />);

    await user.click(screen.getByRole("button", { name: "로그인" }));
    await user.click(screen.getByRole("menuitem", { name: "Naver로 계속" }));

    expect(window.sessionStorage.getItem("kraft-return-to")).toBe("/frequency?limit=100");
  });
});
