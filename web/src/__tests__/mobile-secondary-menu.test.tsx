import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { MobileSecondaryMenu } from "@/components/mobile-secondary-menu";

vi.mock("next/navigation", () => ({
  usePathname: vi.fn().mockReturnValue("/frequency"),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/theme-toggle", () => ({
  ThemeToggle: () => <button data-testid="theme-toggle-stub" />,
}));
vi.mock("@/components/community/account-menu", () => ({
  AccountMenu: () => <div data-testid="account-menu-stub" />,
}));

// matchMedia mock — 리스너를 저장해뒀다가 테스트에서 직접 change 이벤트를 흉내낼 수 있게
// 한다(nav-links.test.tsx가 쓰던 것과 동일한 패턴).
type Listener = (e: MediaQueryListEvent) => void;
let changeListeners: Set<Listener>;

function mockMatchMedia() {
  changeListeners = new Set();
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn((_event: string, cb: Listener) => changeListeners.add(cb)),
      removeEventListener: vi.fn((_event: string, cb: Listener) => changeListeners.delete(cb)),
      dispatchEvent: vi.fn(),
    })),
  });
}

function fireDesktopResize() {
  const event = { matches: true } as MediaQueryListEvent;
  act(() => {
    changeListeners.forEach((cb) => cb(event));
  });
}

describe("MobileSecondaryMenu", () => {
  beforeEach(() => {
    mockMatchMedia();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("기본적으로 드로어가 닫혀 있다", () => {
    render(<MobileSecondaryMenu />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("햄버거 버튼 클릭 시 데이터 링크·서비스 상태·계정·테마를 담은 드로어가 열린다", () => {
    render(<MobileSecondaryMenu />);
    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    for (const label of ["출현 통계", "패턴 통계", "동반 출현", "번호 분석", "서비스 상태"]) {
      expect(screen.getByRole("link", { name: label })).toBeInTheDocument();
    }
    expect(screen.getByTestId("account-menu-stub")).toBeInTheDocument();
    expect(screen.getByTestId("theme-toggle-stub")).toBeInTheDocument();
  });

  it("현재 경로 링크에 aria-current=page를 준다", () => {
    render(<MobileSecondaryMenu />);
    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    expect(screen.getByRole("link", { name: "출현 통계" })).toHaveAttribute("aria-current", "page");
  });

  it("링크 클릭 시 드로어를 닫는다", () => {
    render(<MobileSecondaryMenu />);
    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    fireEvent.click(screen.getByRole("link", { name: "패턴 통계" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("Escape 키를 누르면 드로어를 닫고 햄버거 버튼으로 포커스를 되돌린다", () => {
    render(<MobileSecondaryMenu />);
    const trigger = screen.getByRole("button", { name: "메뉴 열기" });
    fireEvent.click(trigger);
    fireEvent.keyDown(document, { key: "Escape" });

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "메뉴 열기" })).toHaveFocus();
  });

  it("열린 채로 데스크톱 폭까지 리사이즈하면 드로어를 자동으로 닫는다", () => {
    render(<MobileSecondaryMenu />);
    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    fireDesktopResize();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
