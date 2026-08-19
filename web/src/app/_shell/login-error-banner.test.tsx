import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LoginErrorBanner } from "./login-error-banner";

let mockPathname = "/frequency";
let mockSearch = "";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
  useSearchParams: () => new URLSearchParams(mockSearch),
}));

describe("LoginErrorBanner", () => {
  beforeEach(() => {
    mockPathname = "/frequency";
    mockSearch = "";
  });

  it("login_error가 없으면 아무것도 렌더하지 않는다", () => {
    const { container } = render(<LoginErrorBanner />);
    expect(container).toBeEmptyDOMElement();
  });

  // KF-26 UX-05(docs/improvement.md): 재시도 링크가 provider와 무관하게 항상
  // Google로 하드코딩돼 있었다.
  it("KF-26 UX-05: provider=naver면 Naver로 재시도 링크를 만든다", () => {
    mockSearch = "login_error=1&reason=access_denied&provider=naver";
    render(<LoginErrorBanner />);

    const retry = screen.getByRole("link", { name: "Naver로 다시 로그인" });
    expect(retry).toHaveAttribute("href", "/oauth2/authorization/naver");
  });

  it("KF-26 UX-05: provider=google이면 Google로 재시도 링크를 만든다", () => {
    mockSearch = "login_error=1&reason=access_denied&provider=google";
    render(<LoginErrorBanner />);

    const retry = screen.getByRole("link", { name: "Google로 다시 로그인" });
    expect(retry).toHaveAttribute("href", "/oauth2/authorization/google");
  });

  it("KF-26 UX-05: provider가 없으면 Google로 폴백한다", () => {
    mockSearch = "login_error=1&reason=unknown";
    render(<LoginErrorBanner />);

    expect(screen.getByRole("link", { name: "Google로 다시 로그인" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google",
    );
  });

  it("KF-26 UX-05: 허용목록 밖 provider도 Google로 폴백한다", () => {
    mockSearch = "login_error=1&reason=unknown&provider=kakao";
    render(<LoginErrorBanner />);

    expect(screen.getByRole("link", { name: "Google로 다시 로그인" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google",
    );
  });

  it("reason별로 다른 안내 문구를 보여준다", () => {
    mockSearch = "login_error=1&reason=disallowed_useragent";
    render(<LoginErrorBanner />);

    expect(screen.getByText(/인앱 브라우저에서는 구글 로그인이 차단됩니다/)).toBeInTheDocument();
  });
});
