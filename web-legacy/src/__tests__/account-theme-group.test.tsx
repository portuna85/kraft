import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AccountThemeGroup } from "@/components/account-theme-group";

// 이 컴포넌트의 책임은 시각적 그룹화뿐이다 — AccountMenu/ThemeToggle 내부 동작은
// 각자의 테스트가 커버한다(header.test.tsx와 동일한 스텁 방식).
vi.mock("@/components/theme-toggle", () => ({
  ThemeToggle: () => <button data-testid="theme-toggle-stub" />,
}));
vi.mock("@/components/community/account-menu", () => ({
  AccountMenu: () => <div data-testid="account-menu-stub" />,
}));

describe("AccountThemeGroup", () => {
  it("AccountMenu와 ThemeToggle을 함께 렌더링한다", () => {
    render(<AccountThemeGroup />);
    expect(screen.getByTestId("account-menu-stub")).toBeInTheDocument();
    expect(screen.getByTestId("theme-toggle-stub")).toBeInTheDocument();
  });

  it("둘 사이에 구분선(separator)을 둔다", () => {
    render(<AccountThemeGroup />);
    expect(screen.getByRole("separator")).toBeInTheDocument();
  });
});
