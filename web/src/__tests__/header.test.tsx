import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Header } from "@/components/header";

// Header의 책임은 조합(브랜드 링크 + 데스크톱/모바일 내비 묶음)이지 각 자식의 내부 동작이
// 아니다 — 자식은 각자의 테스트(desktop-nav, account-theme-group, mobile-secondary-menu)가
// 이미 커버한다.
vi.mock("@/components/desktop-nav", () => ({
  DesktopNav: () => <nav data-testid="desktop-nav-stub" />,
}));
vi.mock("@/components/account-theme-group", () => ({
  AccountThemeGroup: () => <div data-testid="account-theme-group-stub" />,
}));
vi.mock("@/components/mobile-secondary-menu", () => ({
  MobileSecondaryMenu: () => <div data-testid="mobile-secondary-menu-stub" />,
}));

describe("공용 헤더", () => {
  it("브랜드 링크와 데스크톱 내비·계정+테마 묶음·모바일 보조 메뉴를 모두 렌더링한다", () => {
    render(<Header />);

    const brandLink = screen.getByRole("link", { name: "KRAFT Lotto 홈" });
    expect(brandLink).toHaveAttribute("href", "/");
    expect(screen.getByTestId("desktop-nav-stub")).toBeInTheDocument();
    expect(screen.getByTestId("account-theme-group-stub")).toBeInTheDocument();
    expect(screen.getByTestId("mobile-secondary-menu-stub")).toBeInTheDocument();
  });
});
