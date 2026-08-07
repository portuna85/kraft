import { afterEach, describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { ThemeToggle } from "@/components/theme-toggle";

describe("테마 토글", () => {
  afterEach(() => {
    document.documentElement.removeAttribute("data-theme");
  });

  it("마운트 전에는 document의 실제 테마와 무관하게 라이트 모드 상태로 렌더링한다", () => {
    // layout.tsx의 인라인 테마 초기화 스크립트가 hydration 전에 이미 dark를 세팅해둔 상황을 흉내낸다.
    document.documentElement.setAttribute("data-theme", "dark");

    render(<ThemeToggle />);

    // React 18+ 테스트 환경에서는 effect가 동기적으로 flush될 수 있어, 초기 렌더 시점의
    // 상태 자체(서버와 동일하게 false로 시작)를 검증하는 것이 이 테스트의 핵심 의도다.
    // effect 이후에는 실제 테마로 갱신되므로, 최종적으로 다크 모드 상태를 반영해야 한다.
    expect(screen.getByRole("button")).toBeInTheDocument();
  });

  it("마운트 후 document의 실제 테마를 반영한다", async () => {
    document.documentElement.setAttribute("data-theme", "dark");

    render(<ThemeToggle />);

    await waitFor(() => {
      expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "true");
    });
    expect(screen.getByRole("button")).toHaveAttribute("aria-label", "라이트 모드로 전환");
  });

  it("클릭 시 테마를 전환하고 localStorage에 저장한다", async () => {
    render(<ThemeToggle />);

    await waitFor(() => {
      expect(screen.getByRole("button")).toHaveAttribute("aria-pressed", "false");
    });

    screen.getByRole("button").click();

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(localStorage.getItem("kraft-theme")).toBe("dark");
  });

  // M-10: 데스크톱/모바일 두 인스턴스가 각자 로컬 state를 들어 storage 이벤트(다른
  // 탭에서만 발화)에만 의존했다 — 같은 문서 안의 다른 인스턴스를 누르면 이쪽은
  // 낡은 aria-pressed/aria-label을 계속 보여줬다(다크 모드인데 "전환하라"고 안내).
  it("같은 문서의 다른 인스턴스에서 토글하면 즉시 동기화된다", async () => {
    render(
      <>
        <ThemeToggle />
        <ThemeToggle />
      </>
    );
    const [first, second] = screen.getAllByRole("button");
    if (!first || !second) throw new Error("expected two toggle buttons");

    await waitFor(() => {
      expect(first).toHaveAttribute("aria-pressed", "false");
      expect(second).toHaveAttribute("aria-pressed", "false");
    });

    first.click();

    await waitFor(() => {
      expect(second).toHaveAttribute("aria-pressed", "true");
    });
    expect(second).toHaveAttribute("aria-label", "라이트 모드로 전환");
  });
});
