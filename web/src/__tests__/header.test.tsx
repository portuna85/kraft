import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Header } from "@/components/header";

// Header의 책임은 조합(브랜드 링크 + 세 자식)이지 각 자식의 내부 동작이 아니다 —
// 자식은 각자의 테스트(nav-links, account-menu, theme-toggle)가 이미 커버한다.
vi.mock("@/components/nav-links", () => ({
  NavLinks: () => <nav data-testid="nav-links-stub" />,
}));
vi.mock("@/components/theme-toggle", () => ({
  ThemeToggle: () => <button data-testid="theme-toggle-stub" />,
}));
vi.mock("@/components/community/account-menu", () => ({
  AccountMenu: () => <div data-testid="account-menu-stub" />,
}));

describe("공용 헤더", () => {
  it("브랜드 링크와 내비게이션·계정 메뉴·테마 토글을 모두 렌더링한다", () => {
    render(<Header />);

    const brandLink = screen.getByRole("link", { name: "KRAFT Lotto 홈" });
    expect(brandLink).toHaveAttribute("href", "/");
    expect(screen.getByTestId("nav-links-stub")).toBeInTheDocument();
    expect(screen.getByTestId("account-menu-stub")).toBeInTheDocument();
    expect(screen.getByTestId("theme-toggle-stub")).toBeInTheDocument();
  });
});
