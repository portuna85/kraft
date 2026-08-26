import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { PrimaryNav } from "./primary-nav";

let mockPathname = "/";

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
}));

describe("PrimaryNav", () => {
  beforeEach(() => {
    mockPathname = "/";
  });

  // KF-25②(docs/improvement.md): 데스크톱 내비게이션이 현재 라우트를 노출하지
  // 않았다 — 모바일 TabBar에만 aria-current가 있어 뷰포트별로 동작이 달랐다.
  it("KF-25②: 현재 라우트에만 aria-current=page를 준다", () => {
    mockPathname = "/recommend";
    render(<PrimaryNav />);

    expect(screen.getByRole("link", { name: "번호 추천" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "커뮤니티" })).not.toHaveAttribute("aria-current");
    // kraft-redesign-plan.md P0: "인사이트"는 접힌 그룹이라 현재가 아닐 때
    // data-active를 갖지 않는다.
    expect(screen.getByRole("button", { name: "인사이트" })).not.toHaveAttribute("data-active");
  });

  it("하위 경로도 현재 라우트로 판정한다", () => {
    mockPathname = "/community/posts/1";
    render(<PrimaryNav />);

    expect(screen.getByRole("link", { name: "커뮤니티" })).toHaveAttribute("aria-current", "page");
  });

  /**
   * RSP-23(docs/improvement.md): 기존 세 케이스는 충돌이 없는 경로만 골라
   * 검증했다 — `/community/posts/1`은 매치될 수 있는 항목이 애초에 하나뿐이다.
   * 실제 충돌은 같은 메뉴에 부모(`/recommend`)와 자식(`/recommend/history`)이
   * 함께 있는 곳에서 난다. axe에도 `aria-current` 중복을 잡는 규칙은 없다.
   */
  it("RSP-23: 부모/자식 메뉴가 함께 있어도 현재 페이지는 정확히 하나다", () => {
    mockPathname = "/recommend/history";
    render(<PrimaryNav />);

    const current = screen
      .getAllByRole("link")
      .filter((link) => link.getAttribute("aria-current") === "page");

    expect(current.map((link) => link.textContent)).toEqual(["추천 이력"]);
  });

  it("PRIMARY_NAV 항목과 무관한 경로에서는 아무것도 현재로 표시하지 않는다", () => {
    mockPathname = "/";
    render(<PrimaryNav />);

    for (const link of screen.getAllByRole("link")) {
      expect(link).not.toHaveAttribute("aria-current");
    }
    expect(screen.getByRole("button", { name: "인사이트" })).not.toHaveAttribute("data-active");
  });

  // kraft-redesign-plan.md P0: "인사이트" 그룹은 DropdownMenu 하나로 접힌다 —
  // 하위 라우트가 현재일 때 개별 링크가 아니라 트리거의 data-active로 드러난다.
  it("인사이트 하위 라우트가 현재면 드롭다운 트리거가 data-active를 갖는다", () => {
    mockPathname = "/frequency";
    render(<PrimaryNav />);

    expect(screen.getByRole("button", { name: "인사이트" })).toHaveAttribute("data-active", "true");
  });

  it("인사이트 트리거를 열면 하위 항목이 링크로 나타난다", async () => {
    const user = userEvent.setup();
    render(<PrimaryNav />);

    await user.click(screen.getByRole("button", { name: "인사이트" }));

    expect(screen.getByRole("menuitem", { name: "번호별 출현" })).toHaveAttribute(
      "href",
      "/frequency",
    );
    expect(screen.getByRole("menuitem", { name: "데이터 출처" })).toHaveAttribute(
      "href",
      "/info/data-source",
    );
    expect(screen.getByRole("menuitem", { name: "방법론" })).toHaveAttribute(
      "href",
      "/info/methodology",
    );
  });
});
