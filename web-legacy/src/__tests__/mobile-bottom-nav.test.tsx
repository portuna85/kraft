import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MobileBottomNav } from "@/components/mobile-bottom-nav";

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

describe("MobileBottomNav", () => {
  it("4개 탭(홈/추천/커뮤니티/보관함)을 렌더링한다", () => {
    render(<MobileBottomNav />);
    for (const label of ["홈", "추천", "커뮤니티", "보관함"]) {
      expect(screen.getByRole("link", { name: new RegExp(label) })).toBeInTheDocument();
    }
  });

  it("현재 경로 탭에만 aria-current=page를 준다", () => {
    render(<MobileBottomNav />);
    expect(screen.getByRole("link", { name: /커뮤니티/ })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: /^홈/ })).not.toHaveAttribute("aria-current");
  });

  it("아이콘은 장식용으로 스크린리더에서 숨긴다(텍스트 라벨로 충분)", () => {
    const { container } = render(<MobileBottomNav />);
    expect(container.querySelectorAll("[aria-hidden='true']")).toHaveLength(4);
  });
});
