import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { DesktopNav } from "@/components/desktop-nav";

vi.mock("next/navigation", () => ({
  usePathname: vi.fn().mockReturnValue("/community"),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe("DesktopNav", () => {
  it("주 링크 4개(홈/번호 추천/커뮤니티/보관함)를 렌더링한다", () => {
    render(<DesktopNav />);
    for (const label of ["홈", "번호 추천", "커뮤니티", "보관함"]) {
      expect(screen.getByRole("link", { name: label })).toBeInTheDocument();
    }
  });

  it("현재 경로에 aria-current=page를 준다", () => {
    render(<DesktopNav />);
    expect(screen.getByRole("link", { name: "커뮤니티" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "홈" })).not.toHaveAttribute("aria-current");
  });

  it("데이터 드롭다운은 기본적으로 닫혀 있고, 클릭하면 4개 하위 링크가 나타난다", () => {
    render(<DesktopNav />);
    const toggle = screen.getByRole("button", { name: "데이터" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    for (const label of ["출현 통계", "패턴 통계", "동반 출현", "번호 분석"]) {
      expect(screen.getByRole("link", { name: label })).toBeInTheDocument();
    }
  });

  it("Escape 키를 누르면 데이터 드롭다운을 닫고 토글로 포커스를 되돌린다", () => {
    render(<DesktopNav />);
    const toggle = screen.getByRole("button", { name: "데이터" });
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");

    fireEvent.keyDown(document, { key: "Escape" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(toggle).toHaveFocus();
  });
});
