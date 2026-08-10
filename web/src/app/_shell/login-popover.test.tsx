import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { LoginPopover } from "./login-popover";

describe("로그인 팝오버", () => {
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
});
