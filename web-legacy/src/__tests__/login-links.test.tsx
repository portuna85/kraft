import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { LoginLinks } from "@/features/community/login-links";

const mockUsePathname = vi.fn(() => "/community");
const mockSearchParamsString = vi.fn(() => "");
vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
  useSearchParams: () => ({ toString: () => mockSearchParamsString() }),
}));

const RETURN_TO_KEY = "kraft-community-return-to";

// L-10: pathname만 저장하면 필터·검색 파라미터가 로그인 왕복 후 사라진다.
describe("LoginLinks 복귀 경로", () => {
  it("검색 파라미터가 있으면 pathname과 함께 저장한다", () => {
    mockUsePathname.mockReturnValue("/community");
    mockSearchParamsString.mockReturnValue("category=notice&query=foo");
    window.sessionStorage.clear();

    render(<LoginLinks providers={["google"]} />);
    fireEvent.click(screen.getByRole("link", { name: "Google 로그인" }));

    expect(window.sessionStorage.getItem(RETURN_TO_KEY)).toBe(
      "/community?category=notice&query=foo"
    );
  });

  it("검색 파라미터가 없으면 pathname만 저장한다", () => {
    mockUsePathname.mockReturnValue("/community/write");
    mockSearchParamsString.mockReturnValue("");
    window.sessionStorage.clear();

    render(<LoginLinks providers={["naver"]} />);
    fireEvent.click(screen.getByRole("link", { name: "Naver 로그인" }));

    expect(window.sessionStorage.getItem(RETURN_TO_KEY)).toBe("/community/write");
  });
});
