import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NavLinks } from "@/components/nav-links";

vi.mock("next/navigation", () => ({
  usePathname: vi.fn().mockReturnValue("/"),
}));

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// matchMedia mock — listener 직접 트리거 가능
function makeMatchMedia(initialMatches: boolean) {
  type Listener = (e: MediaQueryListEvent) => void;
  const store = new Map<string, Set<Listener>>();

  const mq = {
    matches: initialMatches,
    media: "",
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn((event: string, cb: Listener) => {
      if (!store.has(event)) store.set(event, new Set());
      store.get(event)!.add(cb);
    }),
    removeEventListener: vi.fn((event: string, cb: Listener) => {
      store.get(event)?.delete(cb);
    }),
    dispatchEvent: vi.fn(),
    trigger(event: string, data: Partial<MediaQueryListEvent>) {
      store.get(event)?.forEach((cb) => cb(data as MediaQueryListEvent));
    },
  };
  return mq;
}

describe("내비게이션 링크", () => {
  let mq: ReturnType<typeof makeMatchMedia>;

  beforeEach(() => {
    mq = makeMatchMedia(false); // 기본: 모바일(<1024px)
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: vi.fn().mockReturnValue(mq),
    });
  });

  it("햄버거 토글 버튼이 렌더링된다", () => {
    render(<NavLinks />);
    expect(screen.getByRole("button", { name: "메뉴 열기" })).toBeInTheDocument();
  });

  it("토글 클릭 시 드로어가 열리고 확장 상태가 참으로 바뀐다", async () => {
    render(<NavLinks />);

    const toggle = screen.getByRole("button", { name: "메뉴 열기" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(toggle);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "메뉴 닫기" })).toHaveAttribute(
        "aria-expanded",
        "true",
      );
    });
    expect(document.getElementById("nav-mobile")).toBeInTheDocument();
  });

  it("드로어가 열리면 스크롤 잠금이 걸린다", async () => {
    render(<NavLinks />);

    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));

    await waitFor(() => {
      expect(document.body.style.overflow).toBe("hidden");
    });
  });

  it("탈출 키로 드로어가 닫히고 토글로 포커스가 복원된다", async () => {
    render(<NavLinks />);

    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "메뉴 닫기" })).toBeInTheDocument(),
    );

    fireEvent.keyDown(document, { key: "Escape" });

    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "메뉴 닫기" })).not.toBeInTheDocument();
    });
    expect(document.getElementById("nav-mobile")).not.toBeInTheDocument();
    // scroll-lock 해제
    expect(document.body.style.overflow).toBe("");
  });

  it("넓은 화면 변경 이벤트로 드로어가 자동 닫힌다", async () => {
    render(<NavLinks />);

    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    await waitFor(() =>
      expect(document.getElementById("nav-mobile")).toBeInTheDocument(),
    );

    act(() => {
      mq.trigger("change", { matches: true } as Partial<MediaQueryListEvent>);
    });

    await waitFor(() => {
      expect(document.getElementById("nav-mobile")).not.toBeInTheDocument();
    });
    // scroll-lock 해제
    expect(document.body.style.overflow).toBe("");
  });

  it("뒷배경 클릭으로 드로어가 닫힌다", async () => {
    render(<NavLinks />);

    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    await waitFor(() =>
      expect(document.getElementById("nav-mobile")).toBeInTheDocument(),
    );

    fireEvent.click(document.querySelector(".nav-backdrop")!);

    await waitFor(() => {
      expect(document.getElementById("nav-mobile")).not.toBeInTheDocument();
    });
  });
});
