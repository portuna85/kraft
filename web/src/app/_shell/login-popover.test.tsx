import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LoginPopover } from "./login-popover";

let mockPathname = "/frequency";
let mockSearch = "";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
  useSearchParams: () => new URLSearchParams(mockSearch),
}));

// provider 링크는 실제 `<a href>` 전체 페이지 이동이다(의도적 설계 — 컴포넌트
// 주석 참조). jsdom은 document 간 이동을 구현하지 않아 클릭 시
// "Not implemented: navigation to another Document"를 찍는다 — 실패는 아니지만
// 이 테스트 파일이 검증하는 행동(href·sessionStorage 저장)과 무관한 잡음이다.
// 클릭을 막지 않고 기본 동작(이동)만 막아 잡음 없이 같은 것을 검증한다.
function preventAnchorNavigation(event: MouseEvent) {
  const anchor = (event.target as HTMLElement).closest("a[href]");
  if (anchor) event.preventDefault();
}

describe("로그인 팝오버", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    mockPathname = "/frequency";
    mockSearch = "";
    document.addEventListener("click", preventAnchorNavigation);
  });

  afterEach(() => {
    document.removeEventListener("click", preventAnchorNavigation);
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
